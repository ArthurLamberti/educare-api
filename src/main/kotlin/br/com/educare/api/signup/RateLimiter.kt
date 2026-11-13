package br.com.educare.api.signup

import br.com.educare.api.common.TooManyRequestsException
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fixed-window counters kept in memory (per API instance, reset on restart). Enough for one instance;
 * move to the database or Redis when the API runs on several.
 */
@Component
class RateLimiter(private val clock: Clock) {

    private data class Window(val start: Instant, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Counts one attempt for [key]; throws 429 when it is past [limit] attempts within [window]. */
    fun hit(key: String, limit: Int, window: Duration) {
        val now = clock.instant()
        val current = windows.compute(key) { _, old ->
            if (old == null || !now.isBefore(old.start.plus(window))) Window(now, 1) else old.copy(count = old.count + 1)
        }!!
        if (current.count > limit) {
            val retryAfter = Duration.between(now, current.start.plus(window)).seconds.coerceAtLeast(1)
            throw TooManyRequestsException(retryAfter)
        }
        if (windows.size > MAX_KEYS) windows.entries.removeIf { !now.isBefore(it.value.start.plus(Duration.ofDays(1))) }
    }

    /** For tests. */
    fun clear() = windows.clear()

    private companion object {
        const val MAX_KEYS = 10_000
    }
}
