package br.com.educare.api.review

import org.springframework.data.jpa.repository.JpaRepository

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface CardReviewRepository : JpaRepository<CardReview, Long> {
    fun findByStudentIdAndCardId(studentId: Long, cardId: Long): CardReview?
}

interface ReviewEventRepository : JpaRepository<ReviewEvent, Long>
