package br.com.educare.api.auth

import br.com.educare.api.common.ProblemResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/** Guards `/api/platform/` with the platform operator key, before the body is even read. */
class PlatformKeyFilter(
    private val properties: SecurityProperties,
    private val problemWriter: ProblemResponseWriter,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("/api/platform/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val given = request.getHeader(HEADER)?.toByteArray() ?: ByteArray(0)
        if (!MessageDigest.isEqual(given, properties.platformKey.toByteArray())) {
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid platform key")
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Platform-Key"
    }
}
