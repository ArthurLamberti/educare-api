package br.com.educare.api.common

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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
            else -> "The data conflicts with existing records"
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail)
    }

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
