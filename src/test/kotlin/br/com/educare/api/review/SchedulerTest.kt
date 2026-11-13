package br.com.educare.api.review

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Pure unit tests of the scheduling rules (no Spring). */
class SchedulerTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    /** Nov 13, 2026, 15:00 in São Paulo (UTC−3). */
    private val now = Instant.parse("2026-11-13T18:00:00Z")

    private fun state(ease: String, interval: Int, reps: Int) = ReviewState(BigDecimal(ease), interval, reps)

    private fun schedule(state: ReviewState, rating: Rating) = Scheduler.schedule(state, rating, now, zone)

    /** Start of the given November 2026 day in São Paulo. */
    private fun startOfNov(day: Int) = Instant.parse("2026-11-%02dT03:00:00Z".format(day))

    @Test
    fun `new card rated GOOD is due at the start of tomorrow in the school zone`() {
        val result = schedule(ReviewState.NEW, Rating.GOOD)
        assertThat(result.state).isEqualTo(state("2.50", 1, 1))
        assertThat(result.dueAt).isEqualTo(startOfNov(14))
    }

    @Test
    fun `second GOOD gives 6 days`() {
        val result = schedule(state("2.50", 1, 1), Rating.GOOD)
        assertThat(result.state).isEqualTo(state("2.50", 6, 2))
        assertThat(result.dueAt).isEqualTo(startOfNov(19))
    }

    @Test
    fun `mature card rated GOOD multiplies by ease`() {
        assertThat(schedule(state("2.50", 10, 3), Rating.GOOD).state).isEqualTo(state("2.50", 25, 4))
    }

    @Test
    fun `forgotten card goes back to relearning in 10 minutes`() {
        val result = schedule(state("2.50", 25, 4), Rating.AGAIN)
        assertThat(result.state).isEqualTo(state("2.30", 0, 0))
        assertThat(result.dueAt).isEqualTo(now.plus(Duration.ofMinutes(10)))
    }

    @Test
    fun `ease never goes below 1_3`() {
        assertThat(schedule(state("1.40", 3, 2), Rating.AGAIN).state.ease).isEqualByComparingTo("1.30")
        assertThat(schedule(state("1.35", 3, 2), Rating.HARD).state.ease).isEqualByComparingTo("1.30")
    }

    @Test
    fun `HARD gives one day first, then 1_2 times the interval`() {
        assertThat(schedule(ReviewState.NEW, Rating.HARD).state).isEqualTo(state("2.35", 1, 1))
        assertThat(schedule(state("2.50", 10, 3), Rating.HARD).state).isEqualTo(state("2.35", 12, 4))
        assertThat(schedule(state("2.50", 1, 1), Rating.HARD).state.intervalDays).isEqualTo(1)
    }

    @Test
    fun `EASY gives 4 days first, then interval times ease times 1_3`() {
        val first = schedule(ReviewState.NEW, Rating.EASY)
        assertThat(first.state).isEqualTo(state("2.65", 4, 1))
        assertThat(first.dueAt).isEqualTo(startOfNov(17))
        // 10 × 2.5 × 1.3 = 32.5 → 33 (with the ease before this rating)
        assertThat(schedule(state("2.50", 10, 3), Rating.EASY).state).isEqualTo(state("2.65", 33, 4))
    }

    @Test
    fun `after AGAIN the next success starts over at one day`() {
        val again = schedule(state("2.50", 25, 4), Rating.AGAIN).state
        assertThat(schedule(again, Rating.GOOD).state).isEqualTo(state("2.30", 1, 1))
    }

    @Test
    fun `due day is computed in the school zone, not UTC`() {
        // 23:30 in São Paulo is already the next day in UTC; tomorrow is still Nov 14 locally.
        val lateEvening = Instant.parse("2026-11-14T02:30:00Z")
        assertThat(Scheduler.schedule(ReviewState.NEW, Rating.GOOD, lateEvening, zone).dueAt).isEqualTo(startOfNov(14))
    }
}
