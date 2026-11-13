package br.com.educare.api.auth

import br.com.educare.api.tenant.TenantInfo
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class TenantSummary(val slug: String, val name: String, val timeZone: String)

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val role: Role,
    val mustChangePassword: Boolean,
    val tenant: TenantSummary,
)

data class LoginResponse(val token: String, val expiresAt: Instant, val user: UserResponse)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val newPassword: String,
)

fun User.toResponse(tenant: TenantInfo) = UserResponse(
    id = requireNotNull(id),
    name = name,
    email = email,
    role = role,
    mustChangePassword = mustChangePassword,
    tenant = TenantSummary(tenant.slug, tenant.name, tenant.timeZone),
)
