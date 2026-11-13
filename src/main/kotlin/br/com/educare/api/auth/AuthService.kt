package br.com.educare.api.auth

import br.com.educare.api.common.EmailNotConfirmedException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.common.UnauthorizedException
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AuthService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokens: TokenService,
    private val tenants: TenantDirectory,
    private val currentUser: CurrentUser,
) {

    /** Same answer for unknown email, wrong password and inactive user, so accounts can't be probed. */
    fun login(request: LoginRequest): LoginResponse {
        val user = users.findByEmail(User.normalizeEmail(request.email))
        if (user == null) {
            passwordEncoder.matches(request.password, dummyHash) // keep timing similar
            throw invalidCredentials()
        }
        if (!passwordEncoder.matches(request.password, user.passwordHash) || !user.active) {
            throw invalidCredentials()
        }
        // Only after the password matched, so this does not reveal which emails signed up.
        if (user.emailConfirmedAt == null) throw EmailNotConfirmedException()
        val issued = tokens.issue(user)
        return LoginResponse(issued.token, issued.expiresAt, user.toResponse(currentTenant()))
    }

    fun me(): UserResponse = loadCurrentUser().toResponse(currentTenant())

    @Transactional
    fun changePassword(request: ChangePasswordRequest) {
        val user = loadCurrentUser()
        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw FieldValidationException("currentPassword", "is incorrect")
        }
        user.passwordHash = requireNotNull(passwordEncoder.encode(request.newPassword))
        user.mustChangePassword = false
    }

    private fun loadCurrentUser(): User =
        users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")

    private fun currentTenant() =
        tenants.findById(requireNotNull(TenantContext.get())) ?: throw NotFoundException("School not found")

    private fun invalidCredentials() = UnauthorizedException("Invalid email or password")

    /** Hash of a random string, compared against when the email does not exist. */
    private val dummyHash: String by lazy { requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString())) }
}
