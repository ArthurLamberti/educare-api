package br.com.educare.api.platform

import br.com.educare.api.tenant.TenantStatus
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
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

    /** Spaces, optionally only those with a status (e.g. `PENDING`, waiting for activation). */
    @GetMapping("/tenants")
    fun list(@RequestParam(required = false) status: TenantStatus?) = service.list(status)

    @PostMapping("/tenants/{id}/activate")
    fun activate(@PathVariable id: Long) = service.activate(id)

    @PostMapping("/tenants/{id}/suspend")
    fun suspend(@PathVariable id: Long) = service.suspend(id)
}
