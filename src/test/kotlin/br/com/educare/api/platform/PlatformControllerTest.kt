package br.com.educare.api.platform

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.json
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post

class PlatformControllerTest : IntegrationTest() {

    private fun provision(body: String, key: String? = "dev-platform-key") = mockMvc.post("/api/platform/tenants") {
        if (key != null) header("X-Platform-Key", key)
        json(body)
    }

    private fun body(slug: String = "alpha", timeZone: String = "America/Sao_Paulo") =
        """{"slug":"$slug","name":"Alpha School","timeZone":"$timeZone",
            "admin":{"name":"Alice","email":"alice@alpha.com","password":"secret123"}}"""

    @Test
    fun `provisions a school whose admin can log in and must change password`() {
        provision(body()).andExpect {
            status { isCreated() }
            jsonPath("$.slug") { value("alpha") }
            jsonPath("$.timeZone") { value("America/Sao_Paulo") }
        }
        login("alpha", "alice@alpha.com", "secret123").andExpect {
            status { isOk() }
            jsonPath("$.user.role") { value("ADMIN") }
            jsonPath("$.user.mustChangePassword") { value(true) }
        }
    }

    @Test
    fun `rejects wrong or missing platform key and creates nothing`() {
        provision(body(), key = "wrong").andExpect { status { isUnauthorized() } }
        provision(body(), key = null).andExpect { status { isUnauthorized() } }
        login("alpha", "alice@alpha.com", "secret123").andExpect { status { isNotFound() } }
    }

    @Test
    fun `rejects duplicate slug`() {
        provision(body()).andExpect { status { isCreated() } }
        provision(body()).andExpect { status { isConflict() } }
    }

    @Test
    fun `rejects invalid slug and time zone`() {
        listOf("Alpha", "my school", "ab", "-alpha").forEach { slug ->
            provision(body(slug = slug)).andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.slug") { exists() }
            }
        }
        provision(body(timeZone = "Mars/Base")).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.timeZone") { exists() }
        }
    }
}
