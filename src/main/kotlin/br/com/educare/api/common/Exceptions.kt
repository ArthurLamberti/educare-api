package br.com.educare.api.common

class NotFoundException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

/** Authentication failed (wrong credentials, bad platform key). Rendered as 401. */
class UnauthorizedException(message: String) : RuntimeException(message)

/** A business validation on a single field. Rendered as 400 with `errors[field] = message`. */
class FieldValidationException(val field: String, message: String) : RuntimeException(message)

/** The request is valid but a business rule refuses it (e.g. too late to ask). Rendered as 422. */
class BusinessRuleException(message: String) : RuntimeException(message)
