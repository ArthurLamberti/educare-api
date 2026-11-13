package br.com.educare.api.user

import br.com.educare.api.tenant.TenantContext
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a token's user is still active, checked on every authenticated request.
 * Cached briefly to avoid a query per request; changes made through this API evict immediately.
 */
@Component
class UserStatusCache(private val repository: UserRepository) {

    private data class Entry(val active: Boolean, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<Pair<Long, Long>, Entry>()

    fun isActive(userId: Long): Boolean {
        val key = (TenantContext.get() ?: TenantContext.UNRESOLVED) to userId
        val now = Instant.now()
        entries[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.active }
        val active = repository.findById(userId).map { it.active }.orElse(false)
        entries[key] = Entry(active, now.plus(TTL))
        return active
    }

    fun evict(userId: Long) {
        entries.keys.removeIf { it.second == userId }
    }

    fun clear() = entries.clear()

    companion object {
        private val TTL = Duration.ofSeconds(30)
    }
}
