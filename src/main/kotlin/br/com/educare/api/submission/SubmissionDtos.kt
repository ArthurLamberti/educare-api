package br.com.educare.api.submission

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class SubmitAnswerRequest(
    @field:NotBlank @field:Size(max = 20000)
    val answer: String,
)

/** The student's private draft; may be blank. */
data class AnswerDraftRequest(
    @field:NotNull @field:Size(max = 20000)
    val answer: String?,
)

/** The teacher's unpublished correction; both fields optional, same rules as a grade when present. */
data class GradeDraftRequest(
    @field:DecimalMin("0.0") @field:DecimalMax("10.0")
    @field:Digits(integer = 2, fraction = 1, message = "must have at most one decimal place")
    val score: BigDecimal? = null,

    @field:Size(max = 10000)
    val feedback: String? = null,

    /** The submission `version` the teacher reviewed; a different current version answers 409. */
    val version: Long? = null,
)

data class GradeRequest(
    @field:NotNull
    @field:DecimalMin("0.0") @field:DecimalMax("10.0")
    @field:Digits(integer = 2, fraction = 1, message = "must have at most one decimal place")
    val score: BigDecimal?,

    @field:NotBlank @field:Size(max = 10000)
    val feedback: String,

    /** The submission `version` the teacher reviewed; a different current version answers 409. */
    val version: Long? = null,
)
