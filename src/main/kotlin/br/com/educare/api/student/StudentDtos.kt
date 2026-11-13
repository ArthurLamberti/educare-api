package br.com.educare.api.student

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class CreateStudentRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:Past
    val birthDate: LocalDate? = null,

    @field:NotBlank @field:Size(min = 8, max = 72)
    val initialPassword: String,

    /** Required for admins; ignored for teachers (the student is theirs). */
    val teacherId: Long? = null,
)

data class UpdateStudentRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:Past
    val birthDate: LocalDate? = null,

    /** Admins only: reassign the student to another active teacher. Ignored for teachers. */
    val teacherId: Long? = null,
)

data class StudentResponse(
    val id: Long,
    val name: String,
    val email: String,
    val birthDate: LocalDate?,
    val teacherId: Long,
    val teacherName: String,
    val active: Boolean,
    val createdAt: Instant,
)

fun Student.toResponse() = StudentResponse(
    id = requireNotNull(id),
    name = user.name,
    email = user.email,
    birthDate = birthDate,
    teacherId = requireNotNull(teacher.id),
    teacherName = teacher.name,
    active = user.active,
    createdAt = user.createdAt,
)
