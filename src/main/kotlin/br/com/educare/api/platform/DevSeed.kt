package br.com.educare.api.platform

import br.com.educare.api.tenant.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Development only: school `demo` (http://demo.localhost:3000) with admin@demo.com / password123. */
@Component
@Profile("dev")
class DevSeed(private val platform: PlatformService, private val tenants: TenantRepository) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (tenants.existsBySlug(SLUG)) return
        platform.provision(
            ProvisionTenantRequest(
                slug = SLUG,
                name = "Demo School",
                timeZone = "America/Sao_Paulo",
                admin = AdminRequest(name = "Demo Admin", email = "admin@demo.com", password = "password123"),
            ),
            adminMustChangePassword = false,
        )
        log.info("Seeded school '{}' with admin@demo.com / password123", SLUG)
    }

    companion object {
        const val SLUG = "demo"
    }
}
