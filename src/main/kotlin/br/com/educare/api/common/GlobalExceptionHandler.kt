package br.com.educare.api.common

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    /** A database constraint caught what the service checks missed (e.g. a concurrent request). */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleIntegrity(ex: DataIntegrityViolationException): ProblemDetail {
        val message = ex.mostSpecificCause.message.orEmpty()
        val detail = when {
            "ex_lessons_teacher_overlap" in message -> "The teacher already has a lesson at this time"
            "ex_lessons_student_overlap" in message -> "The student already has a lesson at this time"
            "ux_reschedule_requests_pending" in message -> "This lesson already has a pending change request"
            "ux_submissions_assignment" in message -> "This assignment already has a submission"
            "ux_assignments_open" in message -> "This task is already assigned to the student"
            "uk_tenants_slug" in message -> "This address is already taken"
            else -> "The data conflicts with existing records"
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail)
    }

    /** Another request changed the same record first (optimistic locking). */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This record was changed by someone else; reload and try again")

    @ExceptionHandler(BusinessRuleException::class)
    fun handleBusinessRule(ex: BusinessRuleException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.message ?: "Not allowed by the school rules")

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(EmailNotConfirmedException::class)
    fun handleEmailNotConfirmed(ex: EmailNotConfirmedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.message ?: "Email not confirmed").apply {
            setProperty("code", "EMAIL_NOT_CONFIRMED")
        }

    @ExceptionHandler(TooManyRequestsException::class)
    fun handleTooManyRequests(ex: TooManyRequestsException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Too many requests"))

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized")

    @ExceptionHandler(FieldValidationException::class)
    fun handleFieldValidation(ex: FieldValidationException): ProblemDetail =
        invalidData(mapOf(ex.field to (ex.message ?: "invalid")))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail =
        invalidData(ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") })

    private fun invalidData(errors: Map<String, String>) =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid data").apply {
            setProperty("errors", errors)
        }
}
