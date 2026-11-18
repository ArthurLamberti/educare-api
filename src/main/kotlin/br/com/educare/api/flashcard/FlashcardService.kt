package br.com.educare.api.flashcard

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.review.ReviewCounters
import br.com.educare.api.student.Student
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.user.Role
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Each student has their own flashcards. Only the student's teacher writes them; the student, their
 * teacher and admins read them. Students the caller may not see are reported as 404.
 */
@Service
@Transactional(readOnly = true)
class FlashcardService(
    private val cards: CardRepository,
    private val students: StudentRepository,
    private val counters: ReviewCounters,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    fun cardsOf(studentId: Long): List<CardResponse> =
        cards.findByStudentIdOrderById(requireNotNull(visibleStudent(studentId).id)).map { it.toResponse() }

    /** The current student's own cards. */
    fun myCards(): List<CardResponse> {
        val student = students.findByUserId(currentUser.id) ?: throw NotFoundException("Student not found")
        return cards.findByStudentIdOrderById(requireNotNull(student.id)).map { it.toResponse() }
    }

    /** One card; a front the student already has (see [Card.keyOf]) is refused with 409. */
    @Transactional
    fun add(studentId: Long, request: CardRequest): CardResponse {
        val result = addNew(studentId, listOf(request))
        if (result.added.isEmpty()) throw duplicate(request.front)
        return result.added.single()
    }

    /** Several cards; duplicates are skipped and reported, and 409 when every card is a duplicate. */
    @Transactional
    fun addAll(studentId: Long, requests: List<CardRequest>): CardBatchResponse =
        addNew(studentId, requests).also {
            if (it.added.isEmpty()) throw ConflictException("All these cards are already in the student's flashcards")
        }

    /**
     * Adds [requests] in order after the student's existing cards, skipping every front the student
     * already has or that appeared earlier in the list. All the new cards are saved or none.
     */
    @Transactional
    fun addNew(studentId: Long, requests: List<CardRequest>): CardBatchResponse {
        val student = ownStudent(studentId, lock = true)
        val existing = cards.findByStudentIdAndFrontKeyIn(studentId, requests.map { Card.keyOf(it.front) }.toSet())
            .mapTo(HashSet()) { it.frontKey }
        val (fresh, skipped) = requests.partition { existing.add(Card.keyOf(it.front)) }

        val count = cards.countByStudentId(studentId)
        if (count + fresh.size > MAX_CARDS) {
            throw ConflictException(
                if (fresh.size == 1) "A student can have at most $MAX_CARDS cards"
                else "A student can have at most $MAX_CARDS cards; they have $count, so at most ${MAX_CARDS - count} more can be added",
            )
        }
        val now = clock.instant()
        val added = cards.saveAllAndFlush(fresh.map { Card(student, it.front.trim(), it.back.trim(), now) })
        return CardBatchResponse(added = added.map { it.toResponse() }, skipped = skipped.map { it.front.trim() })
    }

    @Transactional
    fun update(cardId: Long, request: CardRequest): CardResponse {
        val card = ownCard(cardId)
        val key = Card.keyOf(request.front)
        if (key != card.frontKey) {
            val studentId = requireNotNull(card.student.id)
            if (cards.findByStudentIdAndFrontKeyIn(studentId, setOf(key)).any { it.id != card.id }) throw duplicate(request.front)
        }
        card.edit(request.front.trim(), request.back.trim(), clock.instant())
        return card.toResponse()
    }

    /** Deletes the card and its review history. */
    @Transactional
    fun delete(cardId: Long) {
        cards.delete(ownCard(cardId))
    }

    /** Teachers: their active students. Admins: all active students. Sorted by name. */
    fun progress(): List<FlashcardProgressResponse> {
        val teacherId = if (currentUser.role == Role.TEACHER) currentUser.id else null
        val list = students.search(teacherId, false, Pageable.ofSize(MAX_STUDENTS)).content.sortedBy { it.user.name.lowercase() }
        return counters.forStudents(list)
    }

    private fun visibleStudent(id: Long): Student {
        val student = students.findByIdOrNull(id)
        val visible = when (currentUser.role) {
            Role.ADMIN -> true
            Role.TEACHER -> student?.teacher?.id == currentUser.id
            Role.STUDENT -> student?.user?.id == currentUser.id
        }
        return student?.takeIf { visible } ?: throw NotFoundException("Student $id not found")
    }

    private fun ownStudent(id: Long, lock: Boolean = false): Student =
        (if (lock) students.findByIdForUpdate(id) else students.findByIdOrNull(id))
            ?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Student $id not found")

    private fun ownCard(id: Long): Card =
        cards.findByIdOrNull(id)?.takeIf { it.student.teacher.id == currentUser.id }
            ?: throw NotFoundException("Card $id not found")

    private fun duplicate(front: String) =
        ConflictException("The student already has a card for \"${front.trim()}\"")

    companion object {
        const val MAX_CARDS = 2000
        private const val MAX_STUDENTS = 500
    }
}
