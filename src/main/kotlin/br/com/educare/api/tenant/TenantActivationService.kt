package br.com.educare.api.tenant

import br.com.educare.api.common.BusinessRuleException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.signup.WebProperties
import br.com.educare.api.user.Role
import br.com.educare.api.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** Who turned a space on or off. Today only the operator; a payment gateway webhook will use `PAYMENT`. */
enum class ActivationSource { OPERATOR, PAYMENT }

/** Published inside the transaction; the email to the teacher goes out after it commits. */
data class SpaceActivated(val to: String, val name: String, val spaceName: String, val loginUrl: String)

/**
 * The only place that changes [Tenant.status]. Activation is the step that a confirmed payment will
 * trigger later: the gateway integration only needs to call [activate] with [ActivationSource.PAYMENT]
 * (and [suspend] when a subscription lapses).
 */
@Service
class TenantActivationService(
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val directory: TenantDirectory,
    private val transactions: TransactionTemplate,
    private val events: ApplicationEventPublisher,
    private val web: WebProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Lets the space be used. Refused while no one in it has confirmed the email (the space would
     * still be deleted as an abandoned sign-up). Activating an active space changes nothing.
     */
    fun activate(tenantId: Long, source: ActivationSource): Tenant = change(tenantId) { tenant ->
        if (tenant.status == TenantStatus.ACTIVE) return@change tenant
        val people = users.findAllByTenantId(tenantId).filter { it.role != Role.STUDENT }
        if (people.none { it.emailConfirmedAt != null }) {
            throw BusinessRuleException("The teacher has not confirmed the email yet")
        }
        setStatus(tenant, TenantStatus.ACTIVE, source)
        if (tenant.kind == TenantKind.INDIVIDUAL) {
            people.filter { it.emailConfirmedAt != null }.forEach {
                events.publishEvent(SpaceActivated(it.email, it.name, tenant.name, web.spaceUrl(tenant.slug, "/login")))
            }
        }
        tenant
    }

    /** Turns the space off: its users can still log in, but only see that it is suspended. */
    fun suspend(tenantId: Long, source: ActivationSource): Tenant = change(tenantId) { tenant ->
        if (tenant.status != TenantStatus.SUSPENDED) setStatus(tenant, TenantStatus.SUSPENDED, source)
        tenant
    }

    private fun change(tenantId: Long, block: (Tenant) -> Tenant): Tenant =
        TenantContext.runAs(TenantContext.ROOT) {
            requireNotNull(transactions.execute {
                block(tenants.findByIdOrNull(tenantId) ?: throw NotFoundException("Space not found"))
            }).also { directory.evict(tenantId) }
        }

    private fun setStatus(tenant: Tenant, status: TenantStatus, source: ActivationSource) {
        log.info("Space {} ({}): {} -> {} by {}", tenant.slug, tenant.id, tenant.status, status, source)
        tenant.status = status
        tenant.statusChangedAt = clock.instant()
    }
}
