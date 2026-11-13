package br.com.educare.api.reschedule

import br.com.educare.api.lesson.LessonStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateRescheduleRequest(
    /** Checked against the service clock (future, free); the duration of the lesson is kept. */
    @field:NotNull
    val proposedStartsAt: Instant?,

    @field:Size(max = 500)
    val reason: String? = null,
)

data class RejectRescheduleRequest(
    @field:Size(max = 500)
    val note: String? = null,
)

data class RescheduleRequestResponse(
    val id: Long,
    val lessonId: Long,
    val lessonTopic: String,
    val lessonStatus: LessonStatus,
    val teacherId: Long,
    val teacherName: String,
    val studentId: Long,
    val studentName: String,
    /** The lesson's start now (changes after approval). */
    val lessonStartsAt: Instant,
    val durationMinutes: Int,
    /** The lesson's start when the request was made. */
    val originalStartsAt: Instant,
    val proposedStartsAt: Instant,
    val reason: String?,
    val status: RescheduleStatus,
    val decisionNote: String?,
    val decidedByName: String?,
    val decidedAt: Instant?,
    val createdAt: Instant,
)

fun RescheduleRequest.toResponse() = RescheduleRequestResponse(
    id = requireNotNull(id),
    lessonId = requireNotNull(lesson.id),
    lessonTopic = lesson.topic,
    lessonStatus = lesson.status,
    teacherId = requireNotNull(lesson.teacher.id),
    teacherName = lesson.teacher.name,
    studentId = requireNotNull(lesson.student.id),
    studentName = lesson.student.user.name,
    lessonStartsAt = lesson.startsAt,
    durationMinutes = lesson.durationMinutes,
    originalStartsAt = originalStartsAt,
    proposedStartsAt = proposedStartsAt,
    reason = reason,
    status = status,
    decisionNote = decisionNote,
    decidedByName = decidedBy?.name,
    decidedAt = decidedAt,
    createdAt = createdAt,
)
