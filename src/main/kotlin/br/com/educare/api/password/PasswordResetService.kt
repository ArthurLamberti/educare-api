package br.com.educare.api.password

import br.com.educare.api.common.BadRequestException
import br.com.educare.api.common.TooManyRequestsException
import br.com.educare.api.common.Tokens
import br.com.educare.api.platform.PlatformAdminRepository
import br.com.educare.api.signup.RateLimiter
import br.com.educare.api.signup.WebProperties
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Published inside the transaction; the email goes out after it commits. */
data class PasswordResetRequested(val to: String, val name: String, val place: String, val link: String)

/**
 * "Forgot password" for school users (in the request's tenant) and platform admins. Asking never
 * reveals whether an account exists; every problem with a link gets the same answer.
 */
@Service
class PasswordResetService(
    private val resets: PasswordResetRepository,
    private val users: UserRepository,
    private val admins: PlatformAdminRepository,
    private val directory: TenantDirectory,
    private val passwordEncoder: PasswordEncoder,
    private val transactions: TransactionTemplate,
    private val events: ApplicationEventPublisher,
    private val limiter: RateLimiter,
    private val web: WebProperties,
    private val clock: Clock,
) {

    /** In the request's school or space. Users who never confirmed a sign-up email get nothing. */
    fun forgotForUser(request: ForgotPasswordRequest, clientIp: String) {
        limiter.hit("forgot-ip:$clientIp", 10, Duration.ofHours(1))
        val tenant = requireNotNull(directory.findById(requireNotNull(TenantContext.get())))
        transactions.executeWithoutResult {
            val user = users.findByEmail(User.normalizeEmail(request.email))
                ?.takeIf { it.active && it.emailConfirmedAt != null } ?: return@executeWithoutResult
            if (!withinAccountLimit("forgot-user:${user.id}")) return@executeWithoutResult
            val now = clock.instant()
            resets.invalidateForUser(requireNotNull(user.id), now)
            val token = save(now) { hash, expires -> PasswordReset(userId = user.id, tokenHash = hash, createdAt = now, expiresAt = expires) }
            events.publishEvent(PasswordResetRequested(user.email, user.name, tenant.name, web.spaceUrl(tenant.slug, "/reset-password?token=$token")))
        }
    }

    /** The link only works in the school it was sent for (the user lookup is tenant-filtered). */
    fun resetForUser(request: ResetPasswordRequest, clientIp: String) {
        limiter.hit("reset-ip:$clientIp", 20, Duration.ofHours(1))
        transactions.executeWithoutResult {
            val now = clock.instant()
            val reset = validReset(request.token, now)
            val user = reset.userId?.let { users.findByIdOrNull(it) }?.takeIf { it.active } ?: throw BadRequestException(INVALID_LINK)
            user.passwordHash = encode(request.newPassword)
            user.mustChangePassword = false
            resets.invalidateForUser(requireNotNull(user.id), now)
        }
    }

    fun forgotForAdmin(request: ForgotPasswordRequest, clientIp: String) {
        limiter.hit("forgot-ip:$clientIp", 10, Duration.ofHours(1))
        transactions.executeWithoutResult {
            val admin = admins.findByEmail(User.normalizeEmail(request.email))?.takeIf { it.active } ?: return@executeWithoutResult
            if (!withinAccountLimit("forgot-admin:${admin.id}")) return@executeWithoutResult
            val now = clock.instant()
            resets.invalidateForAdmin(requireNotNull(admin.id), now)
            val token = save(now) { hash, expires -> PasswordReset(platformAdminId = admin.id, tokenHash = hash, createdAt = now, expiresAt = expires) }
            events.publishEvent(PasswordResetRequested(admin.email, admin.name, "painel do operador", web.rootUrl("/admin/reset-password?token=$token")))
        }
    }

    fun resetForAdmin(request: ResetPasswordRequest, clientIp: String) {
        limiter.hit("reset-ip:$clientIp", 20, Duration.ofHours(1))
        transactions.executeWithoutResult {
            val now = clock.instant()
            val reset = validReset(request.token, now)
            val admin = reset.platformAdminId?.let { admins.findByIdOrNull(it) }?.takeIf { it.active } ?: throw BadRequestException(INVALID_LINK)
            admin.passwordHash = encode(request.newPassword)
            resets.invalidateForAdmin(requireNotNull(admin.id), now)
        }
    }

    private fun validReset(token: String, now: Instant): PasswordReset {
        val reset = resets.findByTokenHash(Tokens.hash(token.trim()))
        if (reset == null || reset.usedAt != null || !now.isBefore(reset.expiresAt)) throw BadRequestException(INVALID_LINK)
        return reset
    }

    private fun save(now: Instant, build: (hash: String, expiresAt: Instant) -> PasswordReset): String {
        val token = Tokens.newToken()
        resets.save(build(Tokens.hash(token), now.plus(LINK_TTL)))
        return token
    }

    /** 3 emails per account per hour; past that, silently nothing (a 429 would reveal the account). */
    private fun withinAccountLimit(key: String) =
        try {
            limiter.hit(key, 3, Duration.ofHours(1))
            true
        } catch (_: TooManyRequestsException) {
            false
        }

    private fun encode(password: String) = requireNotNull(passwordEncoder.encode(password))

    companion object {
        val LINK_TTL: Duration = Duration.ofHours(1)
        const val INVALID_LINK = "This link is invalid or has expired"
    }
}
