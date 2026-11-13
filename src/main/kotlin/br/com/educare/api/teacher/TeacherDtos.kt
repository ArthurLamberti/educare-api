package br.com.educare.api.teacher

import br.com.educare.api.user.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateTeacherRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:NotBlank @field:Size(min = 8, max = 72)
    val initialPassword: String,
)

data class UpdateTeacherRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,
)

data class TeacherResponse(
    val id: Long,
    val name: String,
    val email: String,
    val active: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: Instant,
)

fun User.toTeacherResponse() = TeacherResponse(requireNotNull(id), name, email, active, mustChangePassword, createdAt)
