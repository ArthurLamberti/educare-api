package br.com.educare.api.signup

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Hourly; tests call [SignupService.deleteExpiredSignups] directly. */
@Component
@Profile("!test")
class SignupCleanupJob(private val service: SignupService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    fun run() {
        val deleted = service.deleteExpiredSignups()
        if (deleted > 0) log.info("Deleted {} unconfirmed sign-up(s)", deleted)
    }
}
