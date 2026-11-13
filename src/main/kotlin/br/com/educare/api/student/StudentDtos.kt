package br.com.educare.api.student

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class StudentRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:Past
    val birthDate: LocalDate? = null,
)

data class StudentResponse(
    val id: Long,
    val name: String,
    val email: String,
    val birthDate: LocalDate?,
    val createdAt: Instant,
)

fun Student.toResponse() = StudentResponse(
    id = requireNotNull(id),
    name = name,
    email = email,
    birthDate = birthDate,
    createdAt = createdAt,
)
