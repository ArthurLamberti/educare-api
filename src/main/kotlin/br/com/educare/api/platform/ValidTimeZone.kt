package br.com.educare.api.platform

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.ZoneId
import kotlin.reflect.KClass

/** An IANA region id such as `America/Sao_Paulo` (offsets like `+03:00` are rejected). */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TimeZoneValidator::class])
annotation class ValidTimeZone(
    val message: String = "must be a valid IANA time zone, e.g. America/Sao_Paulo",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class TimeZoneValidator : ConstraintValidator<ValidTimeZone, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value in ZoneId.getAvailableZoneIds()
}
