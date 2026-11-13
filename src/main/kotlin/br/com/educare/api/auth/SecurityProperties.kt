package br.com.educare.api.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("educare.security")
data class SecurityProperties(
    /** HMAC secret for access tokens (EDUCARE_JWT_SECRET), at least 32 bytes. */
    val jwtSecret: String,
    /** Platform operator key for `/api/platform/` (EDUCARE_PLATFORM_KEY). */
    val platformKey: String,
    val tokenTtl: Duration = Duration.ofHours(12),
) {
    init {
        require(jwtSecret.toByteArray().size >= 32) { "educare.security.jwt-secret (EDUCARE_JWT_SECRET) must have at least 32 bytes" }
        require(platformKey.isNotBlank()) { "educare.security.platform-key (EDUCARE_PLATFORM_KEY) must be set" }
    }
}
