package br.com.educare.api.lesson

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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
    val status: LessonStatus,
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
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
