package br.com.educare.api.flashcard

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class CardRequest(
    @field:NotBlank @field:Size(max = 1000)
    val front: String,

    @field:NotBlank @field:Size(max = 1000)
    val back: String,
)

/** Several cards at once, added in this order; all are saved or none. */
data class CardBatchRequest(
    @field:NotEmpty @field:Size(max = 500) @field:Valid
    val cards: List<CardRequest>?,
)

data class CardResponse(
    val id: Long,
    val studentId: Long,
    val front: String,
    val back: String,
    /** Never rated by the student. */
    val isNew: Boolean,
    /** Next review; null for new cards. */
    val dueAt: Instant?,
    val intervalDays: Int,
    val lastReviewedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Card.toResponse() = CardResponse(
    id = requireNotNull(id),
    studentId = requireNotNull(student.id),
    front = front,
    back = back,
    isNew = isNew,
    dueAt = dueAt,
    intervalDays = intervalDays,
    lastReviewedAt = lastReviewedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** One student's flashcard counters. "Today" is the school's day. */
data class FlashcardProgressResponse(
    val studentId: Long,
    val name: String,
    val cards: Long,
    val newCards: Long,
    /** Interval under 21 days. */
    val learning: Long,
    /** Interval of 21 days or more. */
    val mature: Long,
    /** Reviewed cards due before the end of today. */
    val dueToday: Long,
    /** New cards still allowed today. */
    val newToday: Long,
    val reviewsLast7Days: Long,
)
