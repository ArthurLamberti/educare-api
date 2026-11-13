package br.com.educare.api.tenant

/**
 * Tenant of the current thread (request). Set by [TenantFilter] before any transaction starts,
 * because Hibernate reads it when a session opens.
 */
object TenantContext {

    /** Bypasses tenant filtering and allows assigning tenant ids explicitly. Platform code only. */
    const val ROOT = -1L

    /** Used when no tenant is set: matches no rows, so missing context fails closed. */
    const val UNRESOLVED = 0L

    private val current = ThreadLocal<Long?>()

    fun get(): Long? = current.get()

    fun set(tenantId: Long) = current.set(tenantId)

    fun clear() = current.remove()

    fun <T> runAs(tenantId: Long, block: () -> T): T {
        val previous = get()
        set(tenantId)
        try {
            return block()
        } finally {
            if (previous == null) clear() else set(previous)
        }
    }
}
