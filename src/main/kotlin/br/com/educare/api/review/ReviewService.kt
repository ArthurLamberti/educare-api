package br.com.educare.api.review

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.flashcard.CardRepository
import br.com.educare.api.flashcard.FlashcardProgressResponse
import br.com.educare.api.student.Student
import br.com.educare.api.student.StudentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * Spaced-repetition reviews (see [Scheduler]) of the current student's own cards. "Today" is the
 * school's day in its time zone. Queries filter on the student id loaded through JPA (tenant scoped).
 */
@Service
@Transactional(readOnly = true)
class ReviewService(
    private val cards: CardRepository,
    private val events: ReviewEventRepository,
    private val students: StudentRepository,
    private val counters: ReviewCounters,
    private val currentUser: CurrentUser,
    private val clock: java.time.Clock,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun rate(request: RateRequest): RateResponse {
        val now = clock.instant()
        val zone = counters.zone()
        val student = students.findByUserIdForUpdate(currentUser.id) ?: throw NotFoundException("Student not found")
        val cardId = requireNotNull(request.cardId)
        val rating = requireNotNull(request.rating)
        val card = cards.findByIdOrNull(cardId)?.takeIf { it.student.id == student.id }
            ?: throw NotFoundException("Card $cardId not found")

        card.review(Scheduler.schedule(card.state, rating, now, zone), now, now.atZone(zone).toLocalDate())
        events.save(ReviewEvent(student, card, rating, now, card.intervalDays))
        cards.flush()
        return RateResponse(cardId, rating, requireNotNull(card.dueAt), card.intervalDays, card.ease, card.repetitions)
    }

    /** Due cards (oldest due first), then new cards in creation order within today's allowance. */
    fun queue(): QueueResponse {
        val now = clock.instant()
        val student = currentStudent()
        val studentId = requireNotNull(student.id)
        val params = mapOf("student" to studentId, "now" to Timestamp.from(now))

        val dueIds = jdbc.queryForList(
            """select id from cards where student_id = :student and due_at <= :now
               order by due_at, id limit $QUEUE_LIMIT""",
            params, Long::class.java,
        ).filterNotNull()
        val dueCount = jdbc.queryForObject(
            "select count(*) from cards where student_id = :student and due_at <= :now", params, Long::class.java,
        ) ?: 0
        val allowance = counters.forStudents(listOf(student)).single().newToday
        val limit = minOf((QUEUE_LIMIT - dueIds.size).toLong(), allowance)
        val newIds = if (limit <= 0) emptyList() else jdbc.queryForList(
            "select id from cards where student_id = :student and due_at is null order by id limit $limit",
            params, Long::class.java,
        ).filterNotNull()

        val loaded = cards.findAllById(dueIds + newIds).associateBy { it.id }
        val queue = (dueIds + newIds).mapNotNull { id -> loaded[id]?.let { QueueCard(id, it.front, it.back, it.isNew, it.dueAt) } }
        val nextDueAt = if (dueIds.isEmpty()) {
            jdbc.query(
                "select min(due_at) from cards where student_id = :student and due_at > :now", params,
            ) { rs, _ -> rs.getTimestamp(1)?.toInstant() }.firstOrNull()
        } else {
            null
        }
        return QueueResponse(dueCount, newIds.size, queue, nextDueAt)
    }

    /** The current student's counters. */
    fun progress(): FlashcardProgressResponse = counters.forStudents(listOf(currentStudent())).single()

    private fun currentStudent(): Student = students.findByUserId(currentUser.id) ?: throw NotFoundException("Student not found")

    companion object {
        const val QUEUE_LIMIT = 200
    }
}
