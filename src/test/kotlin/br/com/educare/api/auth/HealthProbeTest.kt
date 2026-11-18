package br.com.educare.api.auth

import br.com.educare.api.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get

/** The Docker health check calls the probes without a token; readiness includes the database. */
class HealthProbeTest : IntegrationTest() {

    @Test
    fun `health probes answer without authentication`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            mockMvc.get(path).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components") { doesNotExist() }
            }
        }
    }
}
