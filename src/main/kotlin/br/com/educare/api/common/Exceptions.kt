package br.com.educare.api.common

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

/** Authentication failed (wrong credentials, bad platform key). Rendered as 401. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** A business validation on a single field. Rendered as 400 with `errors[field] = message`. */
class FieldValidationException(val field: String, message: String) : RuntimeException(message)

/** A bad request that is not about one field (e.g. an invalid link). Rendered as 400. */
class BadRequestException(message: String) : RuntimeException(message)

/** Correct credentials but the email is not confirmed yet. Rendered as 403 with `code: EMAIL_NOT_CONFIRMED`. */
class EmailNotConfirmedException : RuntimeException("Confirm your email address before logging in")

/** A rate limit was hit. Rendered as 429 with `Retry-After`. */
class TooManyRequestsException(val retryAfterSeconds: Long) : RuntimeException("Too many attempts; try again later")

/** The request is valid but a business rule refuses it (e.g. too late to ask). Rendered as 422. */
class BusinessRuleException(message: String) : RuntimeException(message)
