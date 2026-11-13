package br.com.educare.api.review

import br.com.educare.api.flashcard.Card
import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantOwnedEntity
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** One student's schedule for one card. */
@Entity
@Table(name = "card_reviews")
class CardReview(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    val card: Card,

    @Column(nullable = false, updatable = false)
    val introducedOn: LocalDate,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Column(nullable = false, precision = 4, scale = 2)
    var ease: BigDecimal = ReviewState.NEW.ease
        protected set

    @Column(nullable = false)
    var intervalDays: Int = 0
        protected set

    @Column(nullable = false)
    var repetitions: Int = 0
        protected set

    @Column(nullable = false)
    var dueAt: Instant = Instant.EPOCH
        protected set

    @Column(nullable = false)
    var lastReviewedAt: Instant = Instant.EPOCH
        protected set

    val state: ReviewState get() = ReviewState(ease, intervalDays, repetitions)

    fun apply(scheduled: Scheduled, at: Instant) {
        ease = scheduled.state.ease
        intervalDays = scheduled.state.intervalDays
        repetitions = scheduled.state.repetitions
        dueAt = scheduled.dueAt
        lastReviewedAt = at
    }
}

@Entity
@Table(name = "review_events")
class ReviewEvent(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    val card: Card,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    val rating: Rating,

    @Column(nullable = false, updatable = false)
    val reviewedAt: Instant,

    @Column(nullable = false, updatable = false)
    val intervalDaysAfter: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity()
