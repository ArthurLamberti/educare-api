package br.com.educare.api.tenant

import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findBySlug(slug: String): Tenant?
    fun existsBySlug(slug: String): Boolean
    fun findAllByStatusOrderByCreatedAtAsc(status: TenantStatus): List<Tenant>
    fun findAllByOrderByCreatedAtAsc(): List<Tenant>
}
