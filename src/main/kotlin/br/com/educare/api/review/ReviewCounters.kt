package br.com.educare.api.review

import br.com.educare.api.flashcard.FlashcardProgressResponse
import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.ZoneId

/**
 * Flashcard counters per student. Uses SQL directly; the students passed in were loaded through JPA,
 * which applies the school (tenant) scope, and cards are filtered by those student ids.
 */
@Component
class ReviewCounters(
    private val jdbc: NamedParameterJdbcTemplate,
    private val tenants: TenantDirectory,
    private val clock: Clock,
) {

    fun forStudents(students: List<Student>): List<FlashcardProgressResponse> {
        if (students.isEmpty()) return emptyList()
        val now = clock.instant()
        val zone = zone()
        val params = mapOf(
            "students" to students.map { requireNotNull(it.id) },
            "dayEnd" to Timestamp.from(Scheduler.startOfDay(now, 1, zone)),
            "today" to now.atZone(zone).toLocalDate(),
            "since" to Timestamp.from(now.minus(Duration.ofDays(7))),
        )
        val activity = jdbc.query(
            """select student_id, count(*) from review_events
               where student_id in (:students) and reviewed_at >= :since group by student_id""",
            params,
        ) { rs, _ -> rs.getLong(1) to rs.getLong(2) }.toMap()
        val rows = jdbc.query(
            """select student_id,
                      count(*),
                      count(*) filter (where due_at is null),
                      count(*) filter (where due_at is not null and interval_days < $MATURE_DAYS),
                      count(*) filter (where interval_days >= $MATURE_DAYS),
                      count(*) filter (where due_at < :dayEnd),
                      count(*) filter (where introduced_on = :today)
               from cards where student_id in (:students) group by student_id""",
            params,
        ) { rs, _ -> rs.getLong(1) to LongArray(6) { rs.getLong(it + 2) } }.toMap()

        return students.map { student ->
            val id = requireNotNull(student.id)
            val r = rows[id] ?: LongArray(6)
            FlashcardProgressResponse(
                studentId = id,
                name = student.user.name,
                cards = r[0],
                newCards = r[1],
                learning = r[2],
                mature = r[3],
                dueToday = r[4],
                newToday = minOf(r[1], maxOf(0, NEW_PER_DAY - r[5])),
                reviewsLast7Days = activity[id] ?: 0,
            )
        }
    }

    fun zone(): ZoneId {
        val tenant = tenants.findById(requireNotNull(TenantContext.get())) ?: error("Unknown school")
        return ZoneId.of(tenant.timeZone)
    }

    companion object {
        const val NEW_PER_DAY = 20L
        const val MATURE_DAYS = 21
    }
}
