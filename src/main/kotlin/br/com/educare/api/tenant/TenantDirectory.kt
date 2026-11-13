package br.com.educare.api.tenant

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

data class TenantInfo(val id: Long, val slug: String, val name: String, val timeZone: String, val kind: TenantKind, val status: TenantStatus)

/** Slug lookups for every request, cached in memory; evicted when a tenant is renamed or deleted. */
@Component
class TenantDirectory(private val repository: TenantRepository) {

    private val bySlug = ConcurrentHashMap<String, TenantInfo>()

    fun findBySlug(slug: String): TenantInfo? =
        bySlug[slug] ?: repository.findBySlug(slug)?.toInfo()?.also { bySlug[slug] = it }

    fun findById(id: Long): TenantInfo? =
        bySlug.values.firstOrNull { it.id == id } ?: repository.findById(id).orElse(null)?.toInfo()

    /** Call after changing or deleting a tenant. */
    fun evict(id: Long) {
        bySlug.values.removeIf { it.id == id }
    }

    /** For tests that recreate tenants. */
    fun clear() = bySlug.clear()

    private fun Tenant.toInfo() = TenantInfo(requireNotNull(id), slug, name, timeZone, kind, status)
}
