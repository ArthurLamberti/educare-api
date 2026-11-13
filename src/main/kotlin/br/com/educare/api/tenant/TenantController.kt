package br.com.educare.api.tenant

import br.com.educare.api.auth.TenantSummary
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public: lets the web app show the school's name on the login page (unknown school → 404 by TenantFilter). */
@RestController
@RequestMapping("/api/tenant")
class TenantController(private val directory: TenantDirectory) {

    @GetMapping
    fun current(): TenantSummary {
        val tenant = requireNotNull(directory.findById(requireNotNull(TenantContext.get())))
        return TenantSummary(tenant.slug, tenant.name, tenant.timeZone)
    }
}
