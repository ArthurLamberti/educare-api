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
 * Runs first in the security filter chain; `/api/platform/` is not tenant-scoped.
 */
class TenantFilter(
    private val directory: TenantDirectory,
    private val problemWriter: ProblemResponseWriter,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/api/") || path.startsWith("/api/platform/")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val slug = request.getHeader(HEADER)?.trim()?.lowercase()
        val tenant = slug?.takeIf { it.isNotEmpty() }?.let(directory::findBySlug)
        if (tenant == null) {
            problemWriter.write(request, response, HttpStatus.NOT_FOUND, "School not found")
            return
        }
        TenantContext.set(tenant.id)
        try {
            chain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    companion object {
        const val HEADER = "X-Tenant"
    }
}
