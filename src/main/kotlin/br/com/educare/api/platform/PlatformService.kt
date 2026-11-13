package br.com.educare.api.platform

import br.com.educare.api.common.ConflictException
import br.com.educare.api.tenant.Tenant
import br.com.educare.api.tenant.TenantContext
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
) {

    /**
     * Creates a school and its first admin atomically. Runs as the root tenant, the only context
     * allowed to write rows for a tenant other than the request's.
     */
    fun provision(request: ProvisionTenantRequest, adminMustChangePassword: Boolean = true): TenantResponse =
        TenantContext.runAs(TenantContext.ROOT) {
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
