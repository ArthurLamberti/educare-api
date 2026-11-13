package br.com.educare.api.review

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.flashcard.CardRepository
import br.com.educare.api.flashcard.DeckRepository
import br.com.educare.api.flashcard.DeckStudentId
import br.com.educare.api.flashcard.DeckStudentRepository
import br.com.educare.api.student.Student
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Spaced-repetition reviews (see [Scheduler]). Students review cards of the decks proposed to them;
 * teachers read progress on their decks. "Today" is the school's day in its time zone.
 *
 * Aggregates use SQL directly; every id they filter on was first loaded through JPA, which applies
 * the school (tenant) scope.
 */
@Service
@Transactional(readOnly = true)
class ReviewService(
    private val reviews: CardReviewRepository,
    private val events: ReviewEventRepository,
    private val cards: CardRepository,
    private val decks: DeckRepository,
    private val deckStudents: DeckStudentRepository,
    private val students: StudentRepository,
    private val tenants: TenantDirectory,
    private val currentUser: CurrentUser,
    private val clock: Clock,
    private val jdbc: NamedParameterJdbcTemplate,
) {

    @Transactional
    fun rate(request: RateRequest): RateResponse {
        val now = clock.instant()
        val zone = zone()
        val student = students.findByUserIdForUpdate(currentUser.id) ?: throw NotFoundException("Student not found")
        val cardId = requireNotNull(request.cardId)
        val rating = requireNotNull(request.rating)
        val card = cards.findByIdOrNull(cardId)
            ?.takeIf { deckStudents.existsById(DeckStudentId(requireNotNull(it.deck.id), requireNotNull(student.id))) }
            ?: throw NotFoundException("Card $cardId not found")

        val review = reviews.findByStudentIdAndCardId(requireNotNull(student.id), cardId)
            ?: CardReview(student, card, introducedOn = now.atZone(zone).toLocalDate())
        review.apply(Scheduler.schedule(review.state, rating, now, zone), now)
        reviews.save(review)
        events.save(ReviewEvent(student, card, rating, now, review.intervalDays))
        reviews.flush()
        return RateResponse(cardId, rating, review.dueAt, review.intervalDays, review.ease, review.repetitions)
    }

    /** Due cards (oldest due first), then new cards in deck order within each deck's daily allowance. */
    fun queue(deckId: Long?): QueueResponse {
        val now = clock.instant()
        val zone = zone()
        val student = currentStudent()
        val studentId = requireNotNull(student.id)
        val assigned = deckStudents.findByStudentIdOrderByAssignedAt(studentId).map { requireNotNull(it.deck.id) }
        val deckIds = if (deckId == null) assigned else listOf(deckId).takeIf { deckId in assigned }
            ?: throw NotFoundException("Deck $deckId not found")
        if (deckIds.isEmpty()) return QueueResponse(0, 0, emptyList(), null)

        val params = mapOf("student" to studentId, "decks" to deckIds, "now" to Timestamp.from(now))
        val due = jdbc.query(
            """select cr.card_id, cr.due_at from card_reviews cr join cards c on c.id = cr.card_id
               where cr.student_id = :student and c.deck_id in (:decks) and cr.due_at <= :now
               order by cr.due_at, cr.card_id limit $QUEUE_LIMIT""",
            params,
        ) { rs, _ -> rs.getLong(1) to rs.getTimestamp(2).toInstant() }
        val dueCount = jdbc.queryForObject(
            """select count(*) from card_reviews cr join cards c on c.id = cr.card_id
               where cr.student_id = :student and c.deck_id in (:decks) and cr.due_at <= :now""",
            params, Long::class.java,
        ) ?: 0

        val counts = counts(deckIds, listOf(studentId), now, zone)
        val newIds = mutableListOf<Long>()
        for (deck in deckIds) {
            val room = QUEUE_LIMIT - due.size - newIds.size
            if (room <= 0) break
            val allowance = counts[deck to studentId]?.newToday ?: 0
            val limit = minOf(room.toLong(), allowance)
            if (limit <= 0) continue
            newIds.addAll(jdbc.queryForList(
                """select c.id from cards c where c.deck_id = :deck
                   and not exists (select 1 from card_reviews cr where cr.card_id = c.id and cr.student_id = :student)
                   order by c.position limit $limit""",
                mapOf("deck" to deck, "student" to studentId), Long::class.java,
            ).filterNotNull())
        }

        val loaded = cards.findWithDeckByIdIn(due.map { it.first } + newIds).associateBy { it.id }
        val queue = due.mapNotNull { (id, dueAt) -> loaded[id]?.let { QueueCard(id, requireNotNull(it.deck.id), it.deck.title, it.front, it.back, false, dueAt) } } +
            newIds.mapNotNull { id -> loaded[id]?.let { QueueCard(id, requireNotNull(it.deck.id), it.deck.title, it.front, it.back, true, null) } }
        val nextDueAt = if (due.isEmpty()) {
            jdbc.query(
                """select min(cr.due_at) from card_reviews cr join cards c on c.id = cr.card_id
                   where cr.student_id = :student and c.deck_id in (:decks) and cr.due_at > :now""",
                params,
            ) { rs, _ -> rs.getTimestamp(1)?.toInstant() }.firstOrNull()
        } else {
            null
        }
        return QueueResponse(dueCount, newIds.size, queue, nextDueAt)
    }

    /** The current student's progress on each deck proposed to them. */
    fun studentProgress(): List<DeckProgressResponse> {
        val studentId = requireNotNull(currentStudent().id)
        val assigned = deckStudents.findByStudentIdOrderByAssignedAt(studentId)
        if (assigned.isEmpty()) return emptyList()
        val counts = counts(assigned.map { requireNotNull(it.deck.id) }, listOf(studentId), clock.instant(), zone())
        return assigned.map {
            val deckId = requireNotNull(it.deck.id)
            val c = counts[deckId to studentId] ?: Counts()
            DeckProgressResponse(deckId, it.deck.title, c.newCards, c.learning, c.mature, c.dueToday, c.newToday, c.reviewsLast7Days)
        }
    }

    /** Progress of each student who has the teacher's deck. */
    fun deckProgress(deckId: Long): List<StudentProgressResponse> {
        decks.findByIdOrNull(deckId)?.takeIf { it.teacher.id == currentUser.id } ?: throw NotFoundException("Deck $deckId not found")
        val holders = deckStudents.findByDeckIdOrderByStudentUserName(deckId)
        if (holders.isEmpty()) return emptyList()
        val studentIds = holders.map { requireNotNull(it.student.id) }
        val counts = counts(listOf(deckId), studentIds, clock.instant(), zone())
        return holders.map {
            val studentId = requireNotNull(it.student.id)
            val c = counts[deckId to studentId] ?: Counts()
            StudentProgressResponse(studentId, it.student.user.name, c.newCards, c.learning, c.mature, c.dueToday, c.reviewsLast7Days)
        }
    }

    private data class Counts(
        val newCards: Long = 0,
        val learning: Long = 0,
        val mature: Long = 0,
        val dueToday: Long = 0,
        val newToday: Long = 0,
        val reviewsLast7Days: Long = 0,
    )

    /** Counters per (deck, student) for students who have those decks. */
    private fun counts(deckIds: List<Long>, studentIds: List<Long>, now: Instant, zone: ZoneId): Map<Pair<Long, Long>, Counts> {
        val today: LocalDate = now.atZone(zone).toLocalDate()
        val params = mapOf(
            "decks" to deckIds,
            "students" to studentIds,
            "dayEnd" to Timestamp.from(Scheduler.startOfDay(now, 1, zone)),
            "today" to today,
            "since" to Timestamp.from(now.minus(Duration.ofDays(7))),
        )
        val activity = jdbc.query(
            """select c.deck_id, e.student_id, count(*) from review_events e join cards c on c.id = e.card_id
               where c.deck_id in (:decks) and e.student_id in (:students) and e.reviewed_at >= :since
               group by c.deck_id, e.student_id""",
            params,
        ) { rs, _ -> (rs.getLong(1) to rs.getLong(2)) to rs.getLong(3) }.toMap()

        return jdbc.query(
            """select ds.deck_id, ds.student_id,
                      count(c.id) filter (where cr.id is null),
                      count(cr.id) filter (where cr.interval_days < $MATURE_DAYS),
                      count(cr.id) filter (where cr.interval_days >= $MATURE_DAYS),
                      count(cr.id) filter (where cr.due_at < :dayEnd),
                      count(cr.id) filter (where cr.introduced_on = :today)
               from deck_students ds
               left join cards c on c.deck_id = ds.deck_id
               left join card_reviews cr on cr.card_id = c.id and cr.student_id = ds.student_id
               where ds.deck_id in (:decks) and ds.student_id in (:students)
               group by ds.deck_id, ds.student_id""",
            params,
        ) { rs, _ ->
            val key = rs.getLong(1) to rs.getLong(2)
            val newCards = rs.getLong(3)
            val introducedToday = rs.getLong(7)
            key to Counts(
                newCards = newCards,
                learning = rs.getLong(4),
                mature = rs.getLong(5),
                dueToday = rs.getLong(6),
                newToday = minOf(newCards, maxOf(0, NEW_PER_DAY - introducedToday)),
                reviewsLast7Days = activity[key] ?: 0,
            )
        }.toMap()
    }

    private fun currentStudent(): Student = students.findByUserId(currentUser.id) ?: throw NotFoundException("Student not found")

    private fun zone(): ZoneId {
        val tenant = tenants.findById(requireNotNull(TenantContext.get())) ?: error("Unknown school")
        return ZoneId.of(tenant.timeZone)
    }

    companion object {
        const val NEW_PER_DAY = 20L
        const val QUEUE_LIMIT = 200
        const val MATURE_DAYS = 21
    }
}
