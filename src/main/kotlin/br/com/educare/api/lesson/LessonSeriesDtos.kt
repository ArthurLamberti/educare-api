package br.com.educare.api.lesson

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Lessons every week on [weekdays] at [startTime] (school time), from [startsOn], with no end date. */
data class CreateLessonSeriesRequest(
    @field:NotNull
    val studentId: Long?,

    @field:NotEmpty
    val weekdays: Set<DayOfWeek>?,

    @field:NotNull
    val startsOn: LocalDate?,

    @field:NotNull
    val startTime: LocalTime?,

    @field:NotNull @field:Min(15) @field:Max(240)
    val durationMinutes: Int?,

    @field:NotBlank @field:Size(max = 200)
    val topic: String,

    @field:Size(max = 2000)
    val notes: String? = null,
)

/** Ends the series from [from] (school date, default today): its scheduled lessons from then on are canceled. */
data class EndLessonSeriesRequest(val from: LocalDate? = null)

data class LessonSeriesResponse(
    val id: Long,
    val teacherId: Long,
    val teacherName: String,
    val studentId: Long,
    val studentName: String,
    val weekdays: List<DayOfWeek>,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val topic: String,
    val notes: String?,
    val startsOn: LocalDate,
    val generatedUntil: LocalDate,
    val endedAt: Instant?,
    /** Next scheduled lesson of the series, if any. */
    val nextLessonAt: Instant?,
)

data class CreateLessonSeriesResponse(val series: LessonSeriesResponse, val lessonsCreated: Int)

data class EndLessonSeriesResponse(val series: LessonSeriesResponse, val lessonsCanceled: Int)

fun LessonSeries.toResponse(nextLessonAt: Instant?) = LessonSeriesResponse(
    id = requireNotNull(id),
    teacherId = requireNotNull(teacher.id),
    teacherName = teacher.name,
    studentId = requireNotNull(student.id),
    studentName = student.user.name,
    weekdays = weekdays.sorted(),
    startTime = startTime,
    durationMinutes = durationMinutes,
    topic = topic,
    notes = notes,
    startsOn = startsOn,
    generatedUntil = generatedUntil,
    endedAt = endedAt,
    nextLessonAt = nextLessonAt,
)
