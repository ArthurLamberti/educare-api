package br.com.educare.api.lesson

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.user.Role
import br.com.educare.api.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recurring lessons. Creating a series creates its lessons for the next [HORIZON_DAYS] days, all or
 * nothing (a busy slot is a 409 listing the dates); [extendAll] keeps that horizon as time passes and
 * skips slots that became busy. Ending a series cancels its lessons from a date on.
 */
@Service
@Transactional(readOnly = true)
class LessonSeriesService(
    private val series: LessonSeriesRepository,
    private val lessons: LessonRepository,
    private val students: StudentRepository,
    private val users: UserRepository,
    private val tenants: TenantDirectory,
    private val currentUser: CurrentUser,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Active series: teachers their own, students theirs, admins all. */
    fun list(): List<LessonSeriesResponse> {
        val now = clock.instant()
        return series.findByEndedAtIsNullOrderByCreatedAt().filter { visible(it) }.map { it.toResponse(nextLesson(it, now)) }
    }

    @Transactional
    fun create(request: CreateLessonSeriesRequest): CreateLessonSeriesResponse {
        val now = clock.instant()
        val zone = zone()
        val today = now.atZone(zone).toLocalDate()
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        val student = students.findByIdOrNull(requireNotNull(request.studentId))
            ?.takeIf { it.teacher.id == currentUser.id && it.user.active }
            ?: throw FieldValidationException("studentId", "must be one of your active students")
        val startsOn = requireNotNull(request.startsOn)
        if (startsOn.isBefore(today)) throw FieldValidationException("startsOn", "must be today or later")

        val created = series.save(
            LessonSeries(
                teacher = teacher,
                student = student,
                weekdays = requireNotNull(request.weekdays).toSortedSet(),
                startTime = requireNotNull(request.startTime),
                durationMinutes = requireNotNull(request.durationMinutes),
                topic = request.topic.trim(),
                notes = request.notes?.trim()?.ifEmpty { null },
                startsOn = startsOn,
                createdAt = now,
            ),
        )
        // At least one full week, even when the series starts beyond the horizon.
        val until = maxOf(today.plusDays(HORIZON_DAYS), startsOn.plusDays(6))
        val slots = slots(created, startsOn, until, zone).filter { it.isAfter(now) }
        if (slots.isEmpty()) throw FieldValidationException("weekdays", "no lesson falls in the first week")
        val busy = slots.filter { busy(created, it) }
        if (busy.isNotEmpty()) {
            val dates = busy.joinToString(", ") { it.atZone(zone).toLocalDateTime().toString().replace('T', ' ') }
            throw ConflictException("These lessons clash with other lessons of the teacher or the student: $dates")
        }
        lessons.saveAll(slots.map { lessonAt(created, it) })
        created.generatedUpTo(until, now)
        lessons.flush()
        return CreateLessonSeriesResponse(created.toResponse(slots.first()), slots.size)
    }

    @Transactional
    fun end(id: Long, request: EndLessonSeriesRequest): EndLessonSeriesResponse {
        val now = clock.instant()
        val zone = zone()
        val found = series.findByIdOrNull(id)?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Lesson series $id not found")
        if (!found.active) throw ConflictException("This series has already ended")
        val from = maxOf(now, (request.from ?: now.atZone(zone).toLocalDate()).atStartOfDay(zone).toInstant())
        val canceled = lessons.findScheduledInSeries(requireNotNull(found.id), from)
        canceled.forEach {
            it.cancel()
            events.publishEvent(LessonCanceled(requireNotNull(it.id)))
        }
        found.end(now)
        lessons.flush()
        return EndLessonSeriesResponse(found.toResponse(null), canceled.size)
    }

    /**
     * Creates the lessons of every active series of the current school up to the horizon. Slots that are
     * busy by now are skipped; series of inactive students are ended. Returns the lessons created.
     */
    @Transactional
    fun extendAll(): Int {
        val now = clock.instant()
        val zone = zone()
        val until = now.atZone(zone).toLocalDate().plusDays(HORIZON_DAYS)
        var created = 0
        for (s in series.findByEndedAtIsNullAndGeneratedUntilBefore(until)) {
            if (!s.student.user.active) {
                s.end(now)
                continue
            }
            val slots = slots(s, s.generatedUntil.plusDays(1), until, zone).filter { it.isAfter(now) }
            val (busy, free) = slots.partition { busy(s, it) }
            if (busy.isNotEmpty()) log.info("Series {}: skipped {} busy slot(s)", s.id, busy.size)
            lessons.saveAll(free.map { lessonAt(s, it) })
            s.generatedUpTo(until, now)
            created += free.size
        }
        lessons.flush()
        return created
    }

    /** Start instants of the series' lessons on days [from]..[to] (inclusive), in the school's zone. */
    private fun slots(s: LessonSeries, from: LocalDate, to: LocalDate, zone: ZoneId): List<Instant> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek in s.weekdays }
            .map { it.atTime(s.startTime).atZone(zone).toInstant() }
            .toList()

    private fun busy(s: LessonSeries, startsAt: Instant): Boolean {
        val endsAt = startsAt.plus(Duration.ofMinutes(s.durationMinutes.toLong()))
        return lessons.teacherBusy(requireNotNull(s.teacher.id), startsAt, endsAt, -1) ||
            lessons.studentBusy(requireNotNull(s.student.id), startsAt, endsAt, -1)
    }

    private fun lessonAt(s: LessonSeries, startsAt: Instant) = Lesson(
        teacher = s.teacher,
        student = s.student,
        startsAt = startsAt,
        durationMinutes = s.durationMinutes,
        topic = s.topic,
        notes = s.notes,
        series = s,
        createdAt = clock.instant(),
    )

    private fun nextLesson(s: LessonSeries, now: Instant): Instant? =
        lessons.findScheduledInSeries(requireNotNull(s.id), now).firstOrNull()?.startsAt

    private fun visible(s: LessonSeries) = when (currentUser.role) {
        Role.ADMIN -> true
        Role.TEACHER -> s.teacher.id == currentUser.id
        Role.STUDENT -> s.student.user.id == currentUser.id
    }

    private fun zone(): ZoneId {
        val tenant = tenants.findById(requireNotNull(TenantContext.get())) ?: error("Unknown school")
        return ZoneId.of(tenant.timeZone)
    }

    companion object {
        /** How far ahead lessons of a series exist. */
        const val HORIZON_DAYS = 56L
    }
}
