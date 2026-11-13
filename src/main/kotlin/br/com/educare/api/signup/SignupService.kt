package br.com.educare.api.signup

import br.com.educare.api.common.BadRequestException
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.Tokens
import br.com.educare.api.tenant.Slugs
import br.com.educare.api.tenant.Tenant
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.tenant.TenantKind
import br.com.educare.api.tenant.TenantRepository
import br.com.educare.api.tenant.SpaceAwaitingActivation
import br.com.educare.api.tenant.TenantStatus
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

/** Where the web app runs, to build links in emails: `<scheme>://<slug>.<rootDomain>/...`. */
@ConfigurationProperties("educare.web")
data class WebProperties(val rootDomain: String = "localhost:3000", val scheme: String = "http") {
    fun spaceUrl(slug: String, path: String) = "$scheme://$slug.$rootDomain$path"

    /** On the root domain (landing page, operator panel). */
    fun rootUrl(path: String) = "$scheme://$rootDomain$path"
}

/** Published inside the sign-up transaction; the email goes out only after it commits. */
data class ConfirmationEmailRequested(val to: String, val name: String, val spaceName: String, val link: String)

/**
 * Public sign-up of private teachers. The space starts `PENDING`: after confirming the email the
 * teacher can log in, but uses it only once it is activated (see TenantActivationService). Nobody is logged in and there is no tenant yet, so it runs as
 * the root tenant (like operator provisioning) and scopes lookups explicitly.
 */
@Service
class SignupService(
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val confirmations: EmailConfirmationRepository,
    private val directory: TenantDirectory,
    private val passwordEncoder: PasswordEncoder,
    private val transactions: TransactionTemplate,
    private val jdbc: JdbcTemplate,
    private val events: ApplicationEventPublisher,
    private val limiter: RateLimiter,
    private val web: WebProperties,
    private val clock: Clock,
) {
    fun availability(slug: String, clientIp: String): SlugAvailabilityResponse {
        limiter.hit("slug:$clientIp", 60, Duration.ofMinutes(1))
        val normalized = slug.trim().lowercase()
        val reason = when {
            !Slugs.isValid(normalized) -> SlugUnavailableReason.INVALID
            Slugs.isReserved(normalized) -> SlugUnavailableReason.RESERVED
            TenantContext.runAs(TenantContext.ROOT) { tenants.existsBySlug(normalized) } -> SlugUnavailableReason.TAKEN
            else -> null
        }
        return SlugAvailabilityResponse(normalized, reason == null, reason)
    }

    /**
     * Creates the space and its unconfirmed teacher, then emails the confirmation link.
     * Signing up again for an address that is still unconfirmed, with the same password, corrects
     * that sign-up (e.g. a mistyped email) instead of answering 409.
     */
    fun signUp(request: SignupRequest, clientIp: String): SignupResponse {
        val email = User.normalizeEmail(request.email)
        limiter.hit("signup-ip:$clientIp", 5, Duration.ofHours(1))
        limiter.hit("signup-email:$email", 3, Duration.ofDays(1))
        if (Slugs.isReserved(request.slug)) throw FieldValidationException("slug", "is reserved")

        val now = clock.instant()
        return TenantContext.runAs(TenantContext.ROOT) {
            requireNotNull(transactions.execute {
                tenants.findBySlug(request.slug)?.let { existing ->
                    return@execute retry(existing, request, email)
                }
                val tenant = tenants.save(
                    Tenant(
                        slug = request.slug,
                        name = request.spaceName.trim(),
                        timeZone = request.timeZone,
                        kind = TenantKind.INDIVIDUAL,
                        status = TenantStatus.PENDING,
                        statusChangedAt = now,
                        createdAt = now,
                    ),
                )
                val teacher = users.save(
                    User(
                        name = request.name.trim(),
                        email = email,
                        passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
                        role = Role.TEACHER,
                        emailConfirmedAt = null,
                        createdAt = now,
                    ).apply { tenantId = tenant.id },
                )
                sendLink(tenant, teacher)
                SignupResponse(tenant.slug)
            })
        }
    }

    private fun retry(tenant: Tenant, request: SignupRequest, email: String): SignupResponse {
        val teacher = users.findAllByTenantId(requireNotNull(tenant.id)).singleOrNull()
            ?.takeIf { tenant.kind == TenantKind.INDIVIDUAL && it.emailConfirmedAt == null }
            ?.takeIf { passwordEncoder.matches(request.password, it.passwordHash) }
            ?: throw ConflictException("This address is already taken")
        teacher.name = request.name.trim()
        teacher.email = email
        tenant.name = request.spaceName.trim()
        tenant.timeZone = request.timeZone
        confirmations.invalidateAll(requireNotNull(teacher.id), clock.instant())
        directory.evict(requireNotNull(tenant.id))
        sendLink(tenant, teacher)
        return SignupResponse(tenant.slug)
    }

    /** Uses a link from the email; any problem gets the same answer so tokens cannot be probed. */
    fun confirm(request: ConfirmEmailRequest, clientIp: String): SignupResponse {
        limiter.hit("confirm-ip:$clientIp", 20, Duration.ofHours(1))
        val now = clock.instant()
        return TenantContext.runAs(TenantContext.ROOT) {
            requireNotNull(transactions.execute {
                val confirmation = confirmations.findByTokenHash(hash(request.token.trim()))
                if (confirmation == null || confirmation.usedAt != null || !now.isBefore(confirmation.expiresAt)) {
                    throw BadRequestException(INVALID_LINK)
                }
                val user = users.findByIdOrNull(confirmation.userId) ?: throw BadRequestException(INVALID_LINK)
                confirmation.usedAt = now
                val tenant = tenants.findByIdOrNull(confirmation.tenantId) ?: throw BadRequestException(INVALID_LINK)
                if (user.emailConfirmedAt == null) {
                    user.emailConfirmedAt = now
                    if (tenant.status == TenantStatus.PENDING) {
                        events.publishEvent(SpaceAwaitingActivation(tenant.slug, tenant.name, user.name, user.email))
                    }
                }
                SignupResponse(tenant.slug)
            })
        }
    }

    /** Sends a new link if an unconfirmed account matches; says nothing either way. */
    fun resend(request: ResendConfirmationRequest, clientIp: String) {
        limiter.hit("resend-ip:$clientIp", 10, Duration.ofHours(1))
        val slug = request.slug.trim().lowercase()
        val email = User.normalizeEmail(request.email)
        val tenant = TenantContext.runAs(TenantContext.ROOT) { tenants.findBySlug(slug) }
            ?.takeIf { it.kind == TenantKind.INDIVIDUAL } ?: return
        TenantContext.runAs(requireNotNull(tenant.id)) {
            transactions.executeWithoutResult {
                val user = users.findByEmail(email)?.takeIf { it.emailConfirmedAt == null } ?: return@executeWithoutResult
                limiter.hit("resend-account:${user.id}", 3, Duration.ofHours(1))
                confirmations.invalidateAll(requireNotNull(user.id), clock.instant())
                sendLink(tenant, user)
            }
        }
    }

    /** Deletes private spaces whose teacher never confirmed the email, freeing their addresses. */
    fun deleteExpiredSignups(): Int {
        val cutoff = Timestamp.from(clock.instant().minus(UNCONFIRMED_TTL))
        val ids = jdbc.queryForList(
            """
            SELECT t.id FROM tenants t
            WHERE t.kind = 'INDIVIDUAL' AND t.created_at < ?
              AND NOT EXISTS (SELECT 1 FROM users u WHERE u.tenant_id = t.id AND u.email_confirmed_at IS NOT NULL)
            """.trimIndent(),
            Long::class.java,
            cutoff,
        ).filterNotNull()
        for (id in ids) {
            transactions.executeWithoutResult {
                jdbc.update("DELETE FROM users WHERE tenant_id = ?", id)
                jdbc.update("DELETE FROM tenants WHERE id = ?", id)
            }
            directory.evict(id)
        }
        return ids.size
    }

    private fun sendLink(tenant: Tenant, user: User) {
        val token = Tokens.newToken()
        val now = clock.instant()
        confirmations.save(
            EmailConfirmation(
                tenantId = requireNotNull(tenant.id),
                userId = requireNotNull(user.id),
                tokenHash = hash(token),
                createdAt = now,
                expiresAt = now.plus(LINK_TTL),
            ),
        )
        events.publishEvent(ConfirmationEmailRequested(user.email, user.name, tenant.name, web.spaceUrl(tenant.slug, "/confirm?token=$token")))
    }

    private fun hash(token: String) = Tokens.hash(token)

    companion object {
        val LINK_TTL: Duration = Duration.ofHours(48)
        val UNCONFIRMED_TTL: Duration = Duration.ofDays(7)
        const val INVALID_LINK = "This confirmation link is invalid or has expired"
    }
}
