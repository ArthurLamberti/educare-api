package br.com.educare.api.lesson

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateLessonRequest(
    @field:NotNull
    val studentId: Long?,

    @field:NotNull @field:Future
    val startsAt: Instant?,

    @field:NotNull @field:Min(15) @field:Max(240)
    val durationMinutes: Int?,

    @field:NotBlank @field:Size(max = 200)
    val topic: String,

    @field:Size(max = 2000)
    val notes: String? = null,

    /** Room link of an online lesson (http or https); empty or absent for an in-person lesson. */
    @field:Size(max = 500) @field:Pattern(regexp = MEETING_URL_PATTERN, message = "must be an http or https link")
    val meetingUrl: String? = null,
)

/** `http(s)://` and no spaces (surrounding spaces are trimmed), or empty. */
const val MEETING_URL_PATTERN = "^\\s*$|^\\s*https?://\\S+\\s*$"

/** Empty means none. */
fun normalizeMeetingUrl(url: String?) = url?.trim()?.ifEmpty { null }

/** The teacher's notes about a lesson; empty clears them. */
data class LessonAnnotationsRequest(
    @field:Size(max = 10000)
    val text: String? = null,
)

/** The student of a lesson never changes. */
data class UpdateLessonRequest(
    @field:NotNull @field:Future
    val startsAt: Instant?,

    @field:NotNull @field:Min(15) @field:Max(240)
    val durationMinutes: Int?,

    @field:NotBlank @field:Size(max = 200)
    val topic: String,

    @field:Size(max = 2000)
    val notes: String? = null,

    /** Room link of an online lesson (http or https); empty or absent for an in-person lesson. */
    @field:Size(max = 500) @field:Pattern(regexp = MEETING_URL_PATTERN, message = "must be an http or https link")
    val meetingUrl: String? = null,
)

data class LessonResponse(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val studentId: Long,
    val studentName: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int,
    val topic: String,
    val notes: String?,
    /** Set for online lessons. */
    val meetingUrl: String?,
    /** The teacher's notes about the lesson, visible to the student. */
    val annotations: String?,
    val annotationsUpdatedAt: Instant?,
    val status: LessonStatus,
    /** Set when the lesson belongs to a recurring series. */
    val seriesId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Lesson.toResponse() = LessonResponse(
    id = requireNotNull(id),
    teacherId = requireNotNull(teacher.id),
    teacherName = teacher.name,
    studentId = requireNotNull(student.id),
    studentName = student.user.name,
    startsAt = startsAt,
    endsAt = endsAt,
    durationMinutes = durationMinutes,
    topic = topic,
    notes = notes,
    meetingUrl = meetingUrl,
    annotations = annotations,
    annotationsUpdatedAt = annotationsUpdatedAt,
    status = status,
    seriesId = series?.id,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
