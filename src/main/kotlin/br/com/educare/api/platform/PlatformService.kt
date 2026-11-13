package br.com.educare.api.platform

import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.tenant.Slugs
import br.com.educare.api.tenant.Tenant
import br.com.educare.api.tenant.ActivationSource
import br.com.educare.api.tenant.TenantActivationService
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantKind
import br.com.educare.api.tenant.TenantStatus
import br.com.educare.api.tenant.TenantRepository
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class PlatformService(
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactions: TransactionTemplate,
    private val activation: TenantActivationService,
) {

    /** All spaces, or those with [status], oldest first. */
    fun list(status: TenantStatus?): List<PlatformTenantResponse> = TenantContext.runAs(TenantContext.ROOT) {
        describe(status?.let(tenants::findAllByStatusOrderByCreatedAtAsc) ?: tenants.findAllByOrderByCreatedAtAsc())
    }

    fun activate(id: Long) = describe(activation.activate(id, ActivationSource.OPERATOR))

    fun suspend(id: Long) = describe(activation.suspend(id, ActivationSource.OPERATOR))

    private fun describe(tenant: Tenant) = TenantContext.runAs(TenantContext.ROOT) { describe(listOf(tenant)).single() }

    private fun describe(list: List<Tenant>): List<PlatformTenantResponse> {
        val staff = users.findAllByTenantIdInAndRoleInOrderByIdAsc(list.mapNotNull { it.id }, listOf(Role.ADMIN, Role.TEACHER))
            .groupBy { it.tenantId }
        return list.map { tenant ->
            val ownerRole = if (tenant.kind == TenantKind.INDIVIDUAL) Role.TEACHER else Role.ADMIN
            val owner = staff[tenant.id]?.firstOrNull { it.role == ownerRole }
            tenant.toPlatformResponse(owner?.let { OwnerSummary(it.name, it.email, it.emailConfirmedAt != null) })
        }
    }

    /**
     * Creates a school and its first admin atomically. Runs as the root tenant, the only context
     * allowed to write rows for a tenant other than the request's.
     */
    fun provision(request: ProvisionTenantRequest, adminMustChangePassword: Boolean = true): TenantResponse =
        TenantContext.runAs(TenantContext.ROOT) {
            if (Slugs.isReserved(request.slug)) throw FieldValidationException("slug", "is reserved")
            requireNotNull(transactions.execute {
                if (tenants.existsBySlug(request.slug)) {
                    throw ConflictException("A school with slug '${request.slug}' already exists")
                }
                val tenant = tenants.save(Tenant(slug = request.slug, name = request.name, timeZone = request.timeZone))
                val admin = User(
                    name = request.admin.name,
                    email = request.admin.email,
                    passwordHash = requireNotNull(passwordEncoder.encode(request.admin.password)),
                    role = Role.ADMIN,
                    mustChangePassword = adminMustChangePassword,
                ).apply { tenantId = tenant.id }
                users.save(admin)
                tenant.toResponse()
            })
        }
}

private fun Tenant.toPlatformResponse(owner: OwnerSummary?) =
    PlatformTenantResponse(requireNotNull(id), slug, name, kind, status, timeZone, createdAt, statusChangedAt, owner)
