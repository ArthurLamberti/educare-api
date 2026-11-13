package br.com.educare.api.reschedule

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.BusinessRuleException
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.lesson.Lesson
import br.com.educare.api.lesson.LessonCanceled
import br.com.educare.api.lesson.LessonRepository
import br.com.educare.api.lesson.LessonService
import br.com.educare.api.lesson.LessonStatus
import br.com.educare.api.lesson.LessonTimeChanged
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.springframework.context.event.EventListener
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * Students ask to move their lessons at least [MIN_NOTICE] before the current start; the lesson's
 * teacher approves (moving the lesson with the normal rules) or rejects. Requests the caller may not
 * see are reported as 404.
 */
@Service
@Transactional(readOnly = true)
class RescheduleRequestService(
    private val requests: RescheduleRequestRepository,
    private val lessons: LessonRepository,
    private val lessonService: LessonService,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    fun list(status: RescheduleStatus?, pageable: Pageable): Page<RescheduleRequestResponse> {
        val byStatus = Specification<RescheduleRequest> { root, _, cb ->
            status?.let { cb.equal(root.get<RescheduleStatus>("status"), it) } ?: cb.conjunction()
        }
        return requests.findAll(visibleTo().and(byStatus), pageable).map { it.toResponse() }
    }

    @Transactional
    fun create(lessonId: Long, request: CreateRescheduleRequest): RescheduleRequestResponse {
        val now = clock.instant()
        val lesson = lessons.findByIdOrNull(lessonId)
            ?.takeIf { it.student.user.id == currentUser.id }
            ?: throw NotFoundException("Lesson $lessonId not found")
        if (lesson.status != LessonStatus.SCHEDULED) throw ConflictException("Canceled lessons cannot be changed")
        if (now.isAfter(lesson.startsAt.minus(MIN_NOTICE))) {
            throw BusinessRuleException("Changes must be requested at least 2 days (48 hours) before the lesson starts")
        }
        if (requests.existsByLessonIdAndStatus(lessonId, RescheduleStatus.PENDING)) {
            throw ConflictException("This lesson already has a pending change request")
        }
        val proposed = requireNotNull(request.proposedStartsAt)
        if (!proposed.isAfter(now)) throw FieldValidationException("proposedStartsAt", "must be a future date")
        if (proposed == lesson.startsAt) throw FieldValidationException("proposedStartsAt", "must be different from the current time")
        lessonService.ensureFree(lesson, proposed, lesson.durationMinutes)

        val created = RescheduleRequest(
            lesson = lesson,
            requestedBy = me(),
            originalStartsAt = lesson.startsAt,
            proposedStartsAt = proposed,
            reason = request.reason?.trim()?.ifEmpty { null },
            createdAt = now,
        )
        return requests.saveAndFlush(created).toResponse()
    }

    @Transactional
    fun withdraw(id: Long): RescheduleRequestResponse {
        val request = findForUpdate(id) { it.requestedBy.id == currentUser.id }
        ensurePending(request)
        request.withdraw(clock.instant())
        return request.toResponse()
    }

    /** Moves the lesson; any failure (past time, overlap) rolls back and leaves the request pending. */
    @Transactional
    fun approve(id: Long): RescheduleRequestResponse {
        val request = findForUpdate(id) { it.lesson.teacher.id == currentUser.id }
        ensurePending(request)
        val now = clock.instant()
        if (!request.proposedStartsAt.isAfter(now)) throw ConflictException("The proposed time has already passed")
        val lesson = request.lesson
        lessonService.reschedule(lesson, request.proposedStartsAt, lesson.durationMinutes)
        request.approve(me(), now)
        lessons.saveAndFlush(lesson)
        return request.toResponse()
    }

    @Transactional
    fun reject(id: Long, body: RejectRescheduleRequest): RescheduleRequestResponse {
        val request = findForUpdate(id) { it.lesson.teacher.id == currentUser.id }
        ensurePending(request)
        request.reject(me(), clock.instant(), body.note?.trim()?.ifEmpty { null })
        return request.toResponse()
    }

    /** A lesson moved directly by the teacher or canceled closes its pending request. */
    @EventListener
    fun onLessonTimeChanged(event: LessonTimeChanged) = closePending(event.lessonId)

    @EventListener
    fun onLessonCanceled(event: LessonCanceled) = closePending(event.lessonId)

    private fun closePending(lessonId: Long) {
        val now = clock.instant()
        requests.findByLessonIdAndStatus(lessonId, RescheduleStatus.PENDING).forEach { it.close(now) }
    }

    private fun findForUpdate(id: Long, allowed: (RescheduleRequest) -> Boolean): RescheduleRequest =
        requests.findForUpdate(id)?.takeIf(allowed) ?: throw NotFoundException("Request $id not found")

    private fun ensurePending(request: RescheduleRequest) {
        if (request.status != RescheduleStatus.PENDING) {
            throw ConflictException("This request was already ${request.status.name.lowercase()}")
        }
    }

    private fun me(): User = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")

    private fun visibleTo(): Specification<RescheduleRequest> {
        val userId = currentUser.id
        return when (currentUser.role) {
            Role.ADMIN -> Specification.unrestricted()
            Role.TEACHER -> Specification { root, _, cb ->
                cb.equal(root.get<Lesson>("lesson").get<Any>("teacher").get<Long>("id"), userId)
            }
            Role.STUDENT -> Specification { root, _, cb -> cb.equal(root.get<Any>("requestedBy").get<Long>("id"), userId) }
        }
    }

    companion object {
        val MIN_NOTICE: Duration = Duration.ofHours(48)
    }
}
