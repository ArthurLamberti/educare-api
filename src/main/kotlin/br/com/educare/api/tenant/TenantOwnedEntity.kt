package br.com.educare.api.tenant

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.TenantId

/**
 * Base class for every entity that belongs to a school.
 *
 * Hibernate fills [tenantId] on insert from [TenantContext] and adds `tenant_id = :current`
 * to every query, so code never filters by tenant by hand. Native SQL is NOT filtered.
 */
@MappedSuperclass
abstract class TenantOwnedEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: Long? = null
}
