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
import java.time.Instant

/** Every rating, for activity counters. */
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
