package br.com.educare.api.aluno

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class AlunoRequest(
    @field:NotBlank @field:Size(max = 150)
    val nome: String,

    @field:NotBlank @field:Email @field:Size(max = 255)
    val email: String,

    @field:Past
    val dataNascimento: LocalDate? = null,
)

data class AlunoResponse(
    val id: Long,
    val nome: String,
    val email: String,
    val dataNascimento: LocalDate?,
    val criadoEm: Instant,
)

fun Aluno.toResponse() = AlunoResponse(
    id = requireNotNull(id),
    nome = nome,
    email = email,
    dataNascimento = dataNascimento,
    criadoEm = criadoEm,
)
