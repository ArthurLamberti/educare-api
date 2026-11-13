package br.com.educare.api.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/** All queries are scoped to the current tenant by Hibernate (see TenantOwnedEntity). */
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByEmailAndIdNot(email: String, id: Long): Boolean
    fun findAllByRole(role: Role, pageable: Pageable): Page<User>
    fun findByIdAndRole(id: Long, role: Role): User?

    /** Only meaningful as root (sign-up code), where queries are not tenant-filtered. */
    fun findAllByTenantId(tenantId: Long): List<User>

    /** Root only: admins and teachers of these tenants, oldest first (to find each space's owner). */
    fun findAllByTenantIdInAndRoleInOrderByIdAsc(tenantIds: Collection<Long>, roles: Collection<Role>): List<User>
}
