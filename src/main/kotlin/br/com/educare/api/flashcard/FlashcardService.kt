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

    @Transactional
    fun add(studentId: Long, request: CardRequest): CardResponse = addAll(studentId, listOf(request)).single()

    /** Adds [requests] in order after the student's existing cards; all or nothing. */
    @Transactional
    fun addAll(studentId: Long, requests: List<CardRequest>): List<CardResponse> {
        val student = ownStudent(studentId, lock = true)
        val count = cards.countByStudentId(studentId)
        if (count + requests.size > MAX_CARDS) {
            throw ConflictException(
                if (requests.size == 1) "A student can have at most $MAX_CARDS cards"
                else "A student can have at most $MAX_CARDS cards; they have $count, so at most ${MAX_CARDS - count} more can be added",
            )
        }
        val now = clock.instant()
        return cards.saveAllAndFlush(requests.map { Card(student, it.front.trim(), it.back.trim(), now) }).map { it.toResponse() }
    }

    @Transactional
    fun update(cardId: Long, request: CardRequest): CardResponse {
        val card = ownCard(cardId)
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

    companion object {
        const val MAX_CARDS = 2000
        private const val MAX_STUDENTS = 500
    }
}
