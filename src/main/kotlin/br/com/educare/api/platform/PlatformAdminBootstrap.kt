package br.com.educare.api.platform

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Creates the platform admin from `educare.platform.admin-email` when it does not exist yet. It gets
 * a random password nobody knows: the admin sets one with "Esqueci a senha" on the operator panel,
 * which proves they own the email. So no password ever sits in config files or logs.
 */
@Component
class PlatformAdminBootstrap(
    private val properties: PlatformProperties,
    private val admins: PlatformAdminRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val email = properties.adminEmail.trim().lowercase()
        if (email.isEmpty() || admins.findByEmail(email) != null) return
        admins.save(
            PlatformAdmin(
                name = properties.adminName,
                email = email,
                passwordHash = requireNotNull(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID())),
            ),
        )
        log.info("Created platform admin {}: set the password with \"Esqueci a senha\" at /admin/login", email)
    }
}
