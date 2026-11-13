package br.com.educare.api.flashcard

import br.com.educare.api.review.ReviewState
import br.com.educare.api.review.Scheduled
import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * One flashcard of one student, with that student's spaced-repetition state. Edits keep the id and the
 * state, so fixing a typo does not reset progress. A card never rated has no [dueAt] (it is new).
 */
@Entity
@Table(name = "cards")
class Card(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    front: String,
    back: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 1000)
    var front: String = front
        protected set

    @Column(nullable = false, length = 1000)
    var back: String = back
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @Column(nullable = false, precision = 4, scale = 2)
    var ease: BigDecimal = ReviewState.NEW.ease
        protected set

    @Column(nullable = false)
    var intervalDays: Int = 0
        protected set

    @Column(nullable = false)
    var repetitions: Int = 0
        protected set

    var dueAt: Instant? = null
        protected set

    var lastReviewedAt: Instant? = null
        protected set

    var introducedOn: LocalDate? = null
        protected set

    val isNew: Boolean get() = dueAt == null

    val state: ReviewState get() = ReviewState(ease, intervalDays, repetitions)

    fun edit(front: String, back: String, at: Instant) {
        this.front = front
        this.back = back
        updatedAt = at
    }

    /** Applies a rating made at [at]; [today] is the school day, recorded on the first rating. */
    fun review(scheduled: Scheduled, at: Instant, today: LocalDate) {
        if (introducedOn == null) introducedOn = today
        ease = scheduled.state.ease
        intervalDays = scheduled.state.intervalDays
        repetitions = scheduled.state.repetitions
        dueAt = scheduled.dueAt
        lastReviewedAt = at
    }
}
