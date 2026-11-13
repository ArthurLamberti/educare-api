package br.com.educare.api.signup

import br.com.educare.api.platform.ValidTimeZone
import br.com.educare.api.tenant.Slugs
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:NotBlank @field:Size(min = 8, max = 72)
    val password: String,

    /** Shown in the header, e.g. "Ana Souza – Inglês". */
    @field:NotBlank @field:Size(max = 150)
    val spaceName: String,

    @field:NotBlank
    @field:Pattern(
        regexp = Slugs.PATTERN,
        message = "must be 3-40 lowercase letters, digits or hyphens, not starting or ending with a hyphen",
    )
    val slug: String,

    @field:NotBlank @field:ValidTimeZone
    val timeZone: String,
)

data class SignupResponse(val slug: String)

enum class SlugUnavailableReason { INVALID, RESERVED, TAKEN }

data class SlugAvailabilityResponse(val slug: String, val available: Boolean, val reason: SlugUnavailableReason?)

data class ConfirmEmailRequest(@field:NotBlank @field:Size(max = 100) val token: String)

data class ResendConfirmationRequest(
    @field:NotBlank @field:Size(max = 255) val email: String,
    @field:NotBlank @field:Size(max = 40) val slug: String,
)
