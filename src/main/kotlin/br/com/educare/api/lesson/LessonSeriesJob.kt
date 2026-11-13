package br.com.educare.api.lesson

import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Scheduling is off in tests, which call [LessonSeriesService.extendAll] directly. */
@Configuration
@EnableScheduling
@Profile("!test")
class SchedulingConfiguration

/** Keeps every school's recurring series created [LessonSeriesService.HORIZON_DAYS] ahead. */
@Component
@Profile("!test")
class LessonSeriesJob(private val tenants: TenantRepository, private val service: LessonSeriesService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT6H")
    fun extend() {
        val ids = TenantContext.runAs(TenantContext.ROOT) { tenants.findAll().mapNotNull { it.id } }
        for (id in ids) {
            try {
                val created = TenantContext.runAs(id) { service.extendAll() }
                if (created > 0) log.info("School {}: created {} recurring lesson(s)", id, created)
            } catch (e: Exception) {
                log.error("School {}: could not extend recurring lessons", id, e)
            }
        }
    }
}
