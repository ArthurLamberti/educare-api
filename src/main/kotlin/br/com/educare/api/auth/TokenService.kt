package br.com.educare.api.auth

import br.com.educare.api.user.User
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant

data class IssuedToken(val token: String, val expiresAt: Instant)

@Service
class TokenService(private val encoder: JwtEncoder, private val properties: SecurityProperties) {

    fun issue(user: User, now: Instant = Instant.now()): IssuedToken {
        val expiresAt = now.plus(properties.tokenTtl)
        val claims = JwtClaimsSet.builder()
            .subject(requireNotNull(user.id).toString())
            .claim(TENANT_CLAIM, requireNotNull(user.tenantId))
            .claim(ROLE_CLAIM, user.role.name)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresAt)
    }

    companion object {
        const val TENANT_CLAIM = "tid"
        const val ROLE_CLAIM = "role"
    }
}
