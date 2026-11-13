package br.com.educare.api.tenant

import br.com.educare.api.common.ProblemResponseWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Resolves the school of each `/api/` request from the `X-Tenant` header (the slug, taken from
 * the subdomain by the web app). Unknown or missing tenants get 404 before anything else runs.
 * Runs first in the security filter chain; `/api/platform/` and `/api/public/` (sign-up) are not tenant-scoped.
 *
 * A space that is not `ACTIVE` (waiting for activation, or suspended) answers 403 with
 * `code: ACCOUNT_INACTIVE` to everything except login, the session endpoints and `GET /api/tenant`,
 * so its users can log in and see why they cannot use it yet.
 */
class TenantFilter(
    private val directory: TenantDirectory,
    private val problemWriter: ProblemResponseWriter,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/api/") || path.startsWith("/api/platform/") || path.startsWith("/api/public/")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val slug = request.getHeader(HEADER)?.trim()?.lowercase()
        val tenant = slug?.takeIf { it.isNotEmpty() }?.let(directory::findBySlug)
        if (tenant == null) {
            problemWriter.write(request, response, HttpStatus.NOT_FOUND, "School not found")
            return
        }
        if (tenant.status != TenantStatus.ACTIVE && !allowedWhileInactive(request)) {
            problemWriter.write(
                request, response, HttpStatus.FORBIDDEN, "This space is not active",
                mapOf("code" to "ACCOUNT_INACTIVE", "status" to tenant.status.name),
            )
            return
        }
        TenantContext.set(tenant.id)
        try {
            chain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun allowedWhileInactive(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/auth/") || (path == "/api/tenant" && request.method == "GET")
    }

    companion object {
        const val HEADER = "X-Tenant"
    }
}
