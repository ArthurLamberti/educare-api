package br.com.educare.api.platform

import br.com.educare.api.auth.TokenService
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.common.UnauthorizedException
import br.com.educare.api.password.ForgotPasswordRequest
import br.com.educare.api.password.PasswordResetService
import br.com.educare.api.password.ResetPasswordRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import br.com.educare.api.signup.ClientIp
import br.com.educare.api.signup.RateLimiter
import br.com.educare.api.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class PlatformLoginRequest(@field:NotBlank val email: String, @field:NotBlank val password: String)

data class PlatformAdminResponse(val id: Long, val name: String, val email: String)

data class PlatformLoginResponse(val token: String, val expiresAt: Instant, val admin: PlatformAdminResponse)

fun PlatformAdmin.toResponse() = PlatformAdminResponse(requireNotNull(id), name, email)

@Service
class PlatformAuthService(
    private val admins: PlatformAdminRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokens: TokenService,
    private val limiter: RateLimiter,
) {
    /** Same answer for unknown email, wrong password and inactive admin. 10 attempts per IP in 15 minutes. */
    fun login(request: PlatformLoginRequest, clientIp: String): PlatformLoginResponse {
        limiter.hit("platform-login:$clientIp", 10, Duration.ofMinutes(15))
        val admin = admins.findByEmail(User.normalizeEmail(request.email))
        val matches = passwordEncoder.matches(request.password, admin?.passwordHash ?: dummyHash)
        if (admin == null || !matches || !admin.active) throw UnauthorizedException("Invalid email or password")
        val issued = tokens.issuePlatform(admin)
        return PlatformLoginResponse(issued.token, issued.expiresAt, admin.toResponse())
    }

    fun me(): PlatformAdminResponse {
        val id = SecurityContextHolder.getContext().authentication?.name?.toLongOrNull() ?: throw UnauthorizedException("Authentication required")
        return admins.findByIdOrNull(id)?.toResponse() ?: throw NotFoundException("Admin not found")
    }

    private val dummyHash: String by lazy { requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) }
}

/** Login of platform admins. Login is public; `me` needs a platform admin token. */
@RestController
@RequestMapping("/api/platform/auth")
class PlatformAuthController(
    private val service: PlatformAuthService,
    private val resets: PasswordResetService,
    private val clientIp: ClientIp,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: PlatformLoginRequest, http: HttpServletRequest) = service.login(request, clientIp.of(http))

    @GetMapping("/me")
    fun me() = service.me()

    /** Always 202. Also how the bootstrapped admin sets the first password. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest, http: HttpServletRequest) =
        resets.forgotForAdmin(request, clientIp.of(http))

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest, http: HttpServletRequest) =
        resets.resetForAdmin(request, clientIp.of(http))
}
