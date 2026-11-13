package br.com.educare.api.review

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

enum class Rating { AGAIN, HARD, GOOD, EASY }

/** A card's spaced-repetition state for one student. New cards start with [ReviewState.NEW]. */
data class ReviewState(val ease: BigDecimal, val intervalDays: Int, val repetitions: Int) {
    companion object {
        val NEW = ReviewState(BigDecimal("2.50"), 0, 0)
    }
}

data class Scheduled(val state: ReviewState, val dueAt: Instant)

/**
 * SM-2 variant with four ratings (pure function, no I/O):
 *
 * | rating | ease        | interval (days)                                        | repetitions |
 * |--------|-------------|--------------------------------------------------------|-------------|
 * | AGAIN  | −0.20       | 0, due in 10 minutes                                   | 0           |
 * | HARD   | −0.15       | 1 if repetitions was 0, else max(1, round(i × 1.2))    | +1          |
 * | GOOD   | =           | 1 on the first success, 6 on the second, else round(i × ease) | +1   |
 * | EASY   | +0.15       | 4 on the first success, else round(i × ease × 1.3)     | +1          |
 *
 * Ease never goes below 1.3. Intervals multiply by the ease before this rating. An interval of one day
 * or more is due at the start of that day in the school's time zone.
 */
object Scheduler {

    val MIN_EASE = BigDecimal("1.30")
    val RELEARN_DELAY: Duration = Duration.ofMinutes(10)

    fun schedule(state: ReviewState, rating: Rating, now: Instant, zone: ZoneId): Scheduled {
        val ease = state.ease
        val next = when (rating) {
            Rating.AGAIN -> return Scheduled(ReviewState(floor(ease - BigDecimal("0.20")), 0, 0), now.plus(RELEARN_DELAY))
            Rating.HARD -> ReviewState(
                floor(ease - BigDecimal("0.15")),
                if (state.repetitions == 0) 1 else maxOf(1, round(BigDecimal("1.2") * state.intervalDays)),
                state.repetitions + 1,
            )
            Rating.GOOD -> ReviewState(
                ease,
                when (state.repetitions) {
                    0 -> 1
                    1 -> 6
                    else -> maxOf(1, round(ease * state.intervalDays))
                },
                state.repetitions + 1,
            )
            Rating.EASY -> ReviewState(
                ease + BigDecimal("0.15"),
                if (state.repetitions == 0) 4 else maxOf(1, round(ease * state.intervalDays * BigDecimal("1.3"))),
                state.repetitions + 1,
            )
        }
        return Scheduled(next, startOfDay(now, next.intervalDays, zone))
    }

    /** Start of the day [days] after the day of [now] in [zone]. */
    fun startOfDay(now: Instant, days: Int, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(days.toLong()).atStartOfDay(zone).toInstant()

    private fun floor(ease: BigDecimal) = maxOf(MIN_EASE, ease).setScale(2, RoundingMode.HALF_UP)

    private fun round(value: BigDecimal) = value.setScale(0, RoundingMode.HALF_UP).toInt()

    private operator fun BigDecimal.times(days: Int): BigDecimal = multiply(BigDecimal(days))
}
