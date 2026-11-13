package br.com.educare.api.signup

import br.com.educare.api.auth.SecurityProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/** The browser's IP: from `X-Forwarded-For` when the request comes from a trusted proxy (educare-web). */
@Component
class ClientIp(private val properties: SecurityProperties) {

    fun of(request: HttpServletRequest): String {
        val remote = request.remoteAddr.orEmpty()
        if (remote !in properties.trustedProxies) return remote
        val forwarded = request.getHeader("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
        return forwarded?.takeIf { it.isNotEmpty() } ?: remote
    }
}
