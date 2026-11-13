package br.com.educare.api.auth

import br.com.educare.api.common.ProblemResponseWriter
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.tenant.TenantFilter
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.context.SecurityContextHolderFilter
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tenantDirectory: TenantDirectory,
        properties: SecurityProperties,
        problemWriter: ProblemResponseWriter,
        jwtConverter: TenantAwareJwtConverter,
    ): SecurityFilterChain {
        val entryPoint = AuthenticationEntryPoint { request, response, _ ->
            problemWriter.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required")
        }
        val accessDenied = AccessDeniedHandler { request, response, _ ->
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, "You are not allowed to do this")
        }
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/tenant").permitAll()
                    .requestMatchers("/api/platform/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtConverter) }
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDenied)
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied) }
            .addFilterBefore(TenantFilter(tenantDirectory, problemWriter), SecurityContextHolderFilter::class.java)
            .addFilterBefore(PlatformKeyFilter(properties, problemWriter), SecurityContextHolderFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtSecretKey(properties: SecurityProperties): SecretKey =
        SecretKeySpec(properties.jwtSecret.toByteArray(), "HmacSHA256")

    @Bean
    fun jwtDecoder(key: SecretKey): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key))
}
