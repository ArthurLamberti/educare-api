package br.com.educare.api.auth

import br.com.educare.api.common.ProblemResponseWriter
import br.com.educare.api.common.TooManyRequestsException
import br.com.educare.api.signup.ClientIp
import br.com.educare.api.signup.RateLimiter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Duration

/**
 * Guards `/api/platform/` before the body is even read: either the platform key (`X-Platform-Key`,
 * for scripts) or a platform admin's token (checked by Spring Security, see SecurityConfig).
 * `/api/platform/auth/` (admin login and password reset) is public. Wrong keys are limited per client IP.
 */
class PlatformKeyFilter(
    private val properties: SecurityProperties,
    private val problemWriter: ProblemResponseWriter,
    private val limiter: RateLimiter,
    private val clientIp: ClientIp,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest) =
        !request.requestURI.startsWith("/api/platform/") || request.requestURI.startsWith("/api/platform/auth/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader(HEADER)
        if (header == null && request.getHeader("Authorization")?.startsWith("Bearer ") == true) {
            chain.doFilter(request, response) // a platform admin's token; SecurityConfig checks it
            return
        }
        val given = header?.toByteArray() ?: ByteArray(0)
        if (!MessageDigest.isEqual(given, properties.platformKey.toByteArray())) {
            try {
                limiter.hit("platform-key:${clientIp.of(request)}", MAX_FAILURES, FAILURE_WINDOW)
            } catch (e: TooManyRequestsException) {
                response.setHeader(HttpHeaders.RETRY_AFTER, e.retryAfterSeconds.toString())
                problemWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, e.message ?: "Too many attempts")
                return
            }
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Invalid platform key")
            return
        }
        request.setAttribute(KEY_ACCEPTED, true)
        chain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Platform-Key"
        /** Request attribute set when the platform key was right. */
        const val KEY_ACCEPTED = "educare.platformKeyAccepted"
        private const val MAX_FAILURES = 10
        private val FAILURE_WINDOW: Duration = Duration.ofMinutes(15)
    }
}
