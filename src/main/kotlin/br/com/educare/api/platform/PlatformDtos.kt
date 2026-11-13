package br.com.educare.api.platform

import br.com.educare.api.tenant.Slugs
import br.com.educare.api.tenant.Tenant
import br.com.educare.api.tenant.TenantKind
import br.com.educare.api.tenant.TenantStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class ProvisionTenantRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = Slugs.PATTERN,
        message = "must be 3-40 lowercase letters, digits or hyphens, not starting or ending with a hyphen",
    )
    val slug: String,

    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:ValidTimeZone
    val timeZone: String,

    @field:Valid
    val admin: AdminRequest,
)

data class AdminRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:NotBlank @field:Size(min = 8, max = 72)
    val password: String,
)

data class TenantResponse(val id: Long, val slug: String, val name: String, val timeZone: String, val createdAt: Instant)

fun Tenant.toResponse() = TenantResponse(requireNotNull(id), slug, name, timeZone, createdAt)

/** A space as the operator sees it, with whoever is responsible for it (the private teacher, or the first admin). */
data class PlatformTenantResponse(
    val id: Long,
    val slug: String,
    val name: String,
    val kind: TenantKind,
    val status: TenantStatus,
    val timeZone: String,
    val createdAt: Instant,
    val statusChangedAt: Instant,
    val owner: OwnerSummary?,
)

data class OwnerSummary(val name: String, val email: String, val emailConfirmed: Boolean)
