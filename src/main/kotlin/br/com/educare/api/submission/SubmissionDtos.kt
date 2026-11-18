package br.com.educare.api.submission

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * `answer` is required for tasks without questions; for tasks with questions, `answers` has one
 * non-blank entry per question in order (max 5000 each) and `answer` is optional.
 */
data class SubmitAnswerRequest(
    @field:Size(max = 20000)
    val answer: String? = null,

    @field:Size(max = 50)
    val answers: List<String>? = null,
)

/** The student's private draft; may be blank. */
data class AnswerDraftRequest(
    @field:Size(max = 20000)
    val answer: String? = null,

    /** Draft answers to the questions, by position (may be blank, max 5000 each). */
    @field:Size(max = 50)
    val answers: List<String>? = null,
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
