package br.com.educare.api.tenant

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.auth.TenantSummary
import br.com.educare.api.auth.toSummary
import br.com.educare.api.platform.ValidTimeZone
import br.com.educare.api.user.Role
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class UpdateTenantRequest(
    @field:NotBlank @field:Size(max = 150)
    val name: String,

    @field:NotBlank @field:ValidTimeZone
    val timeZone: String,
)

/**
 * `GET` is public: lets the web app show the school's name on the login page (unknown school → 404
 * by TenantFilter). `PUT` changes name and time zone: school admins, or the teacher of a private space.
 */
@RestController
@RequestMapping("/api/tenant")
class TenantController(
    private val directory: TenantDirectory,
    private val repository: TenantRepository,
    private val currentUser: CurrentUser,
) {

    @GetMapping
    fun current(): TenantSummary = currentInfo().toSummary()

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @Transactional
    fun update(@Valid @RequestBody request: UpdateTenantRequest): TenantSummary {
        val tenant = repository.findById(requireNotNull(TenantContext.get())).orElseThrow()
        val allowed = currentUser.role == Role.ADMIN || (currentUser.role == Role.TEACHER && tenant.kind == TenantKind.INDIVIDUAL)
        if (!allowed) throw AccessDeniedException("Only the school's admins can change its settings")
        tenant.name = request.name.trim()
        tenant.timeZone = request.timeZone
        repository.flush()
        directory.evict(requireNotNull(tenant.id))
        return currentInfo().toSummary()
    }

    private fun currentInfo() = requireNotNull(directory.findById(requireNotNull(TenantContext.get())))
}
