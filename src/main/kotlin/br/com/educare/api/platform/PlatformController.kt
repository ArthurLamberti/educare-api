package br.com.educare.api.platform

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** Platform operator endpoints, guarded by `X-Platform-Key` (see PlatformKeyFilter). */
@RestController
@RequestMapping("/api/platform")
class PlatformController(private val service: PlatformService) {

    @PostMapping("/tenants")
    fun provision(@Valid @RequestBody request: ProvisionTenantRequest): ResponseEntity<TenantResponse> {
        val tenant = service.provision(request)
        return ResponseEntity.created(URI.create("/api/platform/tenants/${tenant.id}")).body(tenant)
    }
}
