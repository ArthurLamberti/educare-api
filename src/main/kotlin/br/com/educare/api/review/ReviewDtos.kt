package br.com.educare.api.review

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant

data class RateRequest(
    @field:NotNull val cardId: Long?,
    @field:NotNull val rating: Rating?,
)

data class RateResponse(
    val cardId: Long,
    val rating: Rating,
    val dueAt: Instant,
    val intervalDays: Int,
    val ease: BigDecimal,
    val repetitions: Int,
)

data class QueueCard(
    val cardId: Long,
    val front: String,
    val back: String,
    /** Never reviewed by this student. */
    val isNew: Boolean,
    /** Null for new cards. */
    val dueAt: Instant?,
)

data class QueueResponse(
    /** All due cards (the list holds at most [ReviewService.QUEUE_LIMIT] cards). */
    val dueCount: Long,
    /** New cards in this queue (limited by the daily allowance). */
    val newCount: Int,
    val cards: List<QueueCard>,
    /** When the next reviewed card becomes due, if none is due now. */
    val nextDueAt: Instant?,
)
