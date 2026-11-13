package br.com.educare.api.signup

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Public: no `X-Tenant` and no token (see TenantFilter and SecurityConfig). Rate limited per client IP. */
@RestController
@RequestMapping("/api/public")
class SignupController(private val service: SignupService, private val clientIp: ClientIp) {

    @GetMapping("/slug-availability")
    fun availability(@RequestParam slug: String, http: HttpServletRequest) = service.availability(slug, clientIp.of(http))

    @PostMapping("/signups")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun signUp(@Valid @RequestBody request: SignupRequest, http: HttpServletRequest) = service.signUp(request, clientIp.of(http))

    @PostMapping("/signups/confirm")
    fun confirm(@Valid @RequestBody request: ConfirmEmailRequest, http: HttpServletRequest) = service.confirm(request, clientIp.of(http))

    @PostMapping("/signups/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resend(@Valid @RequestBody request: ResendConfirmationRequest, http: HttpServletRequest) =
        service.resend(request, clientIp.of(http))
}
