package br.com.educare.api.lesson

import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Duration
import java.time.Instant

enum class LessonStatus { SCHEDULED, CANCELED }

/** A 1:1 lesson. Times are UTC instants; clients render them in the school's time zone. */
@Entity
@Table(name = "lessons")
class Lesson(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    val teacher: User,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    startsAt: Instant,
    durationMinutes: Int,

    @Column(nullable = false, length = 200)
    var topic: String,

    @Column(length = 2000)
    var notes: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwnedEntity() {

    @Column(nullable = false)
    var startsAt: Instant = startsAt
        protected set

    @Column(nullable = false)
    var durationMinutes: Int = durationMinutes
        protected set

    /** Always startsAt + duration (also enforced by a check constraint). */
    @Column(nullable = false)
    var endsAt: Instant = startsAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: LessonStatus = LessonStatus.SCHEDULED
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun changeTime(startsAt: Instant, durationMinutes: Int) {
        this.startsAt = startsAt
        this.durationMinutes = durationMinutes
        this.endsAt = startsAt.plus(Duration.ofMinutes(durationMinutes.toLong()))
        touch()
    }

    fun cancel() {
        status = LessonStatus.CANCELED
        touch()
    }

    fun touch() {
        updatedAt = Instant.now()
    }
}
