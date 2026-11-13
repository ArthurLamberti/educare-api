package br.com.educare.api.auth

import br.com.educare.api.user.Role
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/** The authenticated user of the current request. */
@Component
class CurrentUser {

    private fun token(): JwtAuthenticationToken =
        SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: error("No authenticated user in this request")

    val id: Long get() = requireNotNull(token().token.subject).toLong()

    val role: Role get() = Role.valueOf(requireNotNull(token().token.getClaimAsString(TokenService.ROLE_CLAIM)))
}
