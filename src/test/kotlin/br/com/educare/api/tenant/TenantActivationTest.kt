package br.com.educare.api.tenant

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TenantActivationTest : IntegrationTest() {

    private val operator = "operador@educare.localhost"

    private fun signUp(slug: String = "ana-ingles", email: String = "ana@mail.com") =
        mockMvc.post("/api/public/signups") {
            json(
                """{"name":"Ana Souza","email":"$email","password":"$PASSWORD","spaceName":"Ana – Inglês",
                   "slug":"$slug","timeZone":"America/Sao_Paulo"}""",
            )
        }.andExpect { status { isAccepted() } }

    private fun confirm(email: String = "ana@mail.com") =
        mockMvc.post("/api/public/signups/confirm") { json("""{"token":"${mailer.lastTokenFor(email)}"}""") }
            .andExpect { status { isOk() } }

    private fun idOf(slug: String) = jdbc.queryForObject("SELECT id FROM tenants WHERE slug = ?", Long::class.java, slug)!!

    private fun platform(method: String, path: String, key: String = "dev-platform-key") = when (method) {
        "GET" -> mockMvc.get(path) { header("X-Platform-Key", key) }
        else -> mockMvc.post(path) { header("X-Platform-Key", key) }
    }

    @Test
    fun `a pending space lets its teacher log in but use nothing until the operator activates it`() {
        signUp()
        confirm()
        val token = tokenFor("ana-ingles", "ana@mail.com")

        mockMvc.get("/api/auth/me") { auth("ana-ingles", token) }.andExpect {
            status { isOk() }
            jsonPath("$.tenant.status") { value("PENDING") }
        }
        mockMvc.get("/api/tenant") { header("X-Tenant", "ana-ingles") }.andExpect { jsonPath("$.status") { value("PENDING") } }
        mockMvc.get("/api/students") { auth("ana-ingles", token) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCOUNT_INACTIVE") }
            jsonPath("$.status") { value("PENDING") }
        }
        mockMvc.post("/api/students") {
            auth("ana-ingles", token)
            json("""{"name":"Maria","email":"maria@mail.com","initialPassword":"$PASSWORD"}""")
        }.andExpect { jsonPath("$.code") { value("ACCOUNT_INACTIVE") } }

        platform("POST", "/api/platform/tenants/${idOf("ana-ingles")}/activate").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.owner.email") { value("ana@mail.com") }
        }
        // Same token: the status is checked on every request, not stored in the session.
        mockMvc.get("/api/students") { auth("ana-ingles", token) }.andExpect { status { isOk() } }

        val mail = mailer.sent.last { it.to == "ana@mail.com" }
        assertEquals("Seu espaço na Educare está liberado", mail.subject)
        assertTrue("http://ana-ingles.localhost:3000/login" in mail.text)
    }

    @Test
    fun `the operator is told when a confirmed space waits for activation`() {
        signUp()
        assertTrue(mailer.sent.none { it.to == operator })
        confirm()
        val notice = mailer.sent.single { it.to == operator }
        assertTrue("ana-ingles" in notice.subject)
        assertTrue("ana@mail.com" in notice.text)
    }

    @Test
    fun `the operator lists spaces by status with their owner`() {
        provisionSchool("demo")
        signUp()
        signUp("bia-frances", "bia@mail.com")
        confirm("bia@mail.com")

        platform("GET", "/api/platform/tenants?status=PENDING").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].slug") { value("ana-ingles") }
            jsonPath("$[0].kind") { value("INDIVIDUAL") }
            jsonPath("$[0].owner.emailConfirmed") { value(false) }
            jsonPath("$[1].owner.emailConfirmed") { value(true) }
        }
        platform("GET", "/api/platform/tenants").andExpect {
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].slug") { value("demo") }
            jsonPath("$[0].status") { value("ACTIVE") }
            jsonPath("$[0].owner.email") { value("admin@demo.com") }
        }
        platform("GET", "/api/platform/tenants", key = "wrong").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a space cannot be activated before its teacher confirms the email`() {
        signUp()
        platform("POST", "/api/platform/tenants/${idOf("ana-ingles")}/activate").andExpect {
            status { isUnprocessableContent() }
        }
        platform("POST", "/api/platform/tenants/999/activate").andExpect { status { isNotFound() } }
    }

    @Test
    fun `suspending blocks a space until it is activated again, without a new email`() {
        provisionSchool("demo")
        val admin = tokenFor("demo")
        val id = idOf("demo")

        platform("POST", "/api/platform/tenants/$id/suspend").andExpect { jsonPath("$.status") { value("SUSPENDED") } }
        mockMvc.get("/api/teachers") { auth("demo", admin) }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value("SUSPENDED") }
        }
        login("demo", "admin@demo.com").andExpect {
            status { isOk() }
            jsonPath("$.user.tenant.status") { value("SUSPENDED") }
        }

        platform("POST", "/api/platform/tenants/$id/activate").andExpect { jsonPath("$.status") { value("ACTIVE") } }
        mockMvc.get("/api/teachers") { auth("demo", admin) }.andExpect { status { isOk() } }
        // Schools are not private spaces: no "your space is ready" email.
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `wrong platform keys are limited per client IP`() {
        repeat(10) { platform("GET", "/api/platform/tenants", key = "guess-$it").andExpect { status { isUnauthorized() } } }
        platform("GET", "/api/platform/tenants", key = "guess-10").andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
        }
        // The right key still works.
        platform("GET", "/api/platform/tenants").andExpect { status { isOk() } }
    }
}
