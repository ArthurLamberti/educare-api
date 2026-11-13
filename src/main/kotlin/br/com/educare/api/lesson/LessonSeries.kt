package br.com.educare.api.lesson

import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A weekly pattern of lessons with no end date: on [weekdays] at [startTime] in the school's time zone.
 * Its lessons are ordinary lessons (each can be edited, canceled or moved on its own); the series only
 * creates them, up to [generatedUntil], until it is ended.
 */
@Entity
@Table(name = "lesson_series")
class LessonSeries(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    val teacher: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    @Convert(converter = WeekdaysConverter::class)
    @Column(nullable = false, length = 80, updatable = false)
    val weekdays: Set<DayOfWeek>,

    @Column(nullable = false, updatable = false)
    val startTime: LocalTime,

    @Column(nullable = false, updatable = false)
    val durationMinutes: Int,

    @Column(nullable = false, length = 200, updatable = false)
    val topic: String,

    @Column(length = 2000, updatable = false)
    val notes: String?,

    @Column(nullable = false, updatable = false)
    val startsOn: LocalDate,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    /** Lessons exist for every matching day up to this date (inclusive). */
    @Column(nullable = false)
    var generatedUntil: LocalDate = startsOn.minusDays(1)
        protected set

    var endedAt: Instant? = null
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    val active: Boolean get() = endedAt == null

    fun generatedUpTo(date: LocalDate, at: Instant) {
        generatedUntil = date
        updatedAt = at
    }

    fun end(at: Instant) {
        endedAt = at
        updatedAt = at
    }
}

/** Stores weekdays as "MONDAY,THURSDAY", in week order. */
@Converter
class WeekdaysConverter : AttributeConverter<Set<DayOfWeek>, String> {
    override fun convertToDatabaseColumn(attribute: Set<DayOfWeek>): String = attribute.sorted().joinToString(",")

    override fun convertToEntityAttribute(dbData: String): Set<DayOfWeek> =
        dbData.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSortedSet()
}
