package br.com.educare.api.auth

import br.com.educare.api.password.ForgotPasswordRequest
import br.com.educare.api.password.PasswordResetService
import br.com.educare.api.password.ResetPasswordRequest
import br.com.educare.api.signup.ClientIp
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val service: AuthService,
    private val resets: PasswordResetService,
    private val clientIp: ClientIp,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse = service.login(request)

    @GetMapping("/me")
    fun me(): UserResponse = service.me()

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest) = service.changePassword(request)

    /** Always 202: emails a link only if an active account with this email exists in this school. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest, http: HttpServletRequest) =
        resets.forgotForUser(request, clientIp.of(http))

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest, http: HttpServletRequest) =
        resets.resetForUser(request, clientIp.of(http))
}
