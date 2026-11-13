package br.com.educare.api.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Writes a ProblemDetail-shaped body from filters, where @RestControllerAdvice does not apply. */
@Component
class ProblemResponseWriter(private val jsonMapper: JsonMapper) {

    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String,
        extra: Map<String, Any> = emptyMap(),
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = mapOf(
            "type" to "about:blank",
            "title" to status.reasonPhrase,
            "status" to status.value(),
            "detail" to detail,
            "instance" to request.requestURI,
        ) + extra
        jsonMapper.writeValue(response.outputStream, body)
    }
}
