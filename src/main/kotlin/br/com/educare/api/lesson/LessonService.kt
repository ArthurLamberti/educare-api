package br.com.educare.api.lesson

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.student.Student
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.user.Role
import br.com.educare.api.user.UserRepository
import jakarta.persistence.criteria.JoinType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class LessonFilter(
    val from: Instant? = null,
    val to: Instant? = null,
    val status: LessonStatus? = null,
    val studentId: Long? = null,
    val teacherId: Long? = null,
)

/**
 * Lessons of the current school. Teachers manage their own; students read their own; admins read all.
 * Lessons the caller may not see are reported as 404.
 */
@Service
@Transactional(readOnly = true)
class LessonService(
    private val lessons: LessonRepository,
    private val students: StudentRepository,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {

    fun list(filter: LessonFilter, pageable: Pageable): Page<LessonResponse> =
        lessons.findAll(visibleTo().and(filter.toSpec()), pageable).map { it.toResponse() }

    fun get(id: Long): LessonResponse = findVisible(id).toResponse()

    @Transactional
    fun create(request: CreateLessonRequest): LessonResponse {
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        val student = ownActiveStudent(requireNotNull(request.studentId))
        val startsAt = requireNotNull(request.startsAt)
        val duration = requireNotNull(request.durationMinutes)
        checkFree(requireNotNull(teacher.id), requireNotNull(student.id), startsAt, duration, excludeId = null)
        val lesson = Lesson(
            teacher = teacher,
            student = student,
            startsAt = startsAt,
            durationMinutes = duration,
            topic = request.topic.trim(),
            notes = request.notes?.trim()?.ifEmpty { null },
        )
        return lessons.saveAndFlush(lesson).toResponse()
    }

    @Transactional
    fun update(id: Long, request: UpdateLessonRequest): LessonResponse {
        val lesson = findOwnScheduled(id)
        val startsAt = requireNotNull(request.startsAt)
        val duration = requireNotNull(request.durationMinutes)
        if (startsAt != lesson.startsAt || duration != lesson.durationMinutes) {
            reschedule(lesson, startsAt, duration)
            events.publishEvent(LessonTimeChanged(requireNotNull(lesson.id)))
        }
        lesson.topic = request.topic.trim()
        lesson.notes = request.notes?.trim()?.ifEmpty { null }
        lesson.touch()
        return lessons.saveAndFlush(lesson).toResponse()
    }

    @Transactional
    fun cancel(id: Long): LessonResponse {
        val lesson = findOwnScheduled(id)
        lesson.cancel()
        events.publishEvent(LessonCanceled(requireNotNull(lesson.id)))
        return lesson.toResponse()
    }

    /**
     * The single path that changes a lesson's time: validates the slot and checks overlaps.
     * Also used when a reschedule request is approved (it publishes no event).
     */
    fun reschedule(lesson: Lesson, startsAt: Instant, durationMinutes: Int) {
        if (!startsAt.isAfter(clock.instant())) throw FieldValidationException("startsAt", "must be a future date")
        ensureFree(lesson, startsAt, durationMinutes)
        lesson.changeTime(startsAt, durationMinutes)
    }

    /** 409 if the lesson's teacher or student has another scheduled lesson in that range. */
    fun ensureFree(lesson: Lesson, startsAt: Instant, durationMinutes: Int) =
        checkFree(requireNotNull(lesson.teacher.id), requireNotNull(lesson.student.id), startsAt, durationMinutes, lesson.id)

    private fun checkFree(teacherId: Long, studentId: Long, startsAt: Instant, durationMinutes: Int, excludeId: Long?) {
        val endsAt = startsAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
        val exclude = excludeId ?: -1
        if (lessons.teacherBusy(teacherId, startsAt, endsAt, exclude)) {
            throw ConflictException("The teacher already has a lesson at this time")
        }
        if (lessons.studentBusy(studentId, startsAt, endsAt, exclude)) {
            throw ConflictException("The student already has a lesson at this time")
        }
    }

    private fun ownActiveStudent(studentId: Long): Student =
        students.findByIdOrNull(studentId)
            ?.takeIf { it.teacher.id == currentUser.id && it.user.active }
            ?: throw FieldValidationException("studentId", "must be one of your active students")

    private fun findVisible(id: Long): Lesson =
        lessons.findOne(visibleTo().and { root, _, cb -> cb.equal(root.get<Long>("id"), id) }).orElse(null)
            ?: throw NotFoundException("Lesson $id not found")

    private fun findOwnScheduled(id: Long): Lesson {
        val lesson = lessons.findByIdOrNull(id)
            ?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Lesson $id not found")
        if (lesson.status != LessonStatus.SCHEDULED) throw ConflictException("Canceled lessons cannot be changed")
        return lesson
    }

    /** Role scope: teachers see their lessons, students theirs, admins all of the school. */
    private fun visibleTo(): Specification<Lesson> {
        val userId = currentUser.id
        return when (currentUser.role) {
            Role.ADMIN -> Specification.unrestricted()
            Role.TEACHER -> Specification { root, _, cb -> cb.equal(root.get<Any>("teacher").get<Long>("id"), userId) }
            Role.STUDENT -> Specification { root, _, cb ->
                cb.equal(root.join<Any, Any>("student", JoinType.INNER).get<Any>("user").get<Long>("id"), userId)
            }
        }
    }

    private fun LessonFilter.toSpec(): Specification<Lesson> = Specification { root, _, cb ->
        val predicates = buildList {
            from?.let { add(cb.greaterThanOrEqualTo(root.get("startsAt"), it)) }
            to?.let { add(cb.lessThan(root.get("startsAt"), it)) }
            status?.let { add(cb.equal(root.get<LessonStatus>("status"), it)) }
            studentId?.takeIf { currentUser.role != Role.STUDENT }
                ?.let { add(cb.equal(root.get<Any>("student").get<Long>("id"), it)) }
            teacherId?.takeIf { currentUser.role == Role.ADMIN }
                ?.let { add(cb.equal(root.get<Any>("teacher").get<Long>("id"), it)) }
        }
        cb.and(*predicates.toTypedArray())
    }
}
