package br.com.educare.api.flashcard

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class DeckRequest(
    @field:NotBlank @field:Size(max = 200)
    val title: String,

    @field:Size(max = 2000)
    val description: String? = null,
)

data class DeckResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val archived: Boolean,
    val teacherId: Long,
    val teacherName: String,
    val cardCount: Long,
    /** Students the deck is proposed to; null for students. */
    val studentCount: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Deck.toResponse(cardCount: Long, studentCount: Long?) = DeckResponse(
    id = requireNotNull(id),
    title = title,
    description = description,
    archived = archived,
    teacherId = requireNotNull(teacher.id),
    teacherName = teacher.name,
    cardCount = cardCount,
    studentCount = studentCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

data class CardRequest(
    @field:NotBlank @field:Size(max = 1000)
    val front: String,

    @field:NotBlank @field:Size(max = 1000)
    val back: String,
)

data class CardResponse(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    /** 0-based position in the deck. */
    val position: Int,
    val updatedAt: Instant,
)

fun Card.toResponse() = CardResponse(requireNotNull(id), requireNotNull(deck.id), front, back, position, updatedAt)

/** The deck's full card list in the new order. */
data class ReorderCardsRequest(
    @field:NotNull
    val cardIds: List<Long>?,
)

data class DeckStudentsRequest(
    @field:NotEmpty @field:Size(max = 200)
    val studentIds: List<Long>?,
)

data class DeckStudentResponse(
    val studentId: Long,
    val name: String,
    val assignedAt: Instant,
)

fun DeckStudent.toResponse() = DeckStudentResponse(requireNotNull(student.id), student.user.name, assignedAt)
