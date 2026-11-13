package br.com.educare.api.auth

import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.user.UserStatusCache
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Turns a verified JWT into an authentication, rejecting (401) tokens issued for another school
 * than the request's `X-Tenant`, and tokens of users deactivated since login.
 */
@Component
class TenantAwareJwtConverter(private val userStatus: UserStatusCache) : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val tokenTenant = (jwt.claims[TokenService.TENANT_CLAIM] as? Number)?.toLong()
        if (tokenTenant == null || tokenTenant != TenantContext.get()) {
            throw InvalidBearerTokenException("Token does not belong to this school")
        }
        val subject = jwt.subject ?: throw InvalidBearerTokenException("Invalid token subject")
        val userId = subject.toLongOrNull() ?: throw InvalidBearerTokenException("Invalid token subject")
        if (!userStatus.isActive(userId)) {
            throw InvalidBearerTokenException("User is no longer active")
        }
        val role = jwt.getClaimAsString(TokenService.ROLE_CLAIM)
        return JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("ROLE_$role")), subject)
    }
}
