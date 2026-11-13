package br.com.educare.api.password

import br.com.educare.api.platform.PlatformAdminBootstrap
import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordResetTest : IntegrationTest() {

    @Autowired lateinit var bootstrap: PlatformAdminBootstrap

    private val admin = "arthurlamberti001@gmail.com"

    private fun forgot(slug: String, email: String) = mockMvc.post("/api/auth/forgot-password") {
        header("X-Tenant", slug)
        json("""{"email":"$email"}""")
    }

    private fun reset(slug: String, token: String, password: String = "new-password-1") = mockMvc.post("/api/auth/reset-password") {
        header("X-Tenant", slug)
        json("""{"token":"$token","newPassword":"$password"}""")
    }

    @Test
    fun `a school user resets the password with a single-use link`() {
        provisionSchool("demo")
        forgot("demo", "ADMIN@demo.com").andExpect { status { isAccepted() } }
        val mail = mailer.sent.single()
        assertEquals("Criar nova senha na Educare", mail.subject)
        assertTrue("http://demo.localhost:3000/reset-password?token=" in mail.text)
        val token = mailer.lastTokenFor("admin@demo.com")

        reset("demo", token, "short").andExpect { status { isBadRequest() } }
        reset("demo", token).andExpect { status { isNoContent() } }
        login("demo", "admin@demo.com").andExpect { status { isUnauthorized() } }
        login("demo", "admin@demo.com", "new-password-1").andExpect { status { isOk() } }
        reset("demo", token, "another-password").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("This link is invalid or has expired") }
        }
    }

    @Test
    fun `asking never reveals accounts and a new link replaces the old one`() {
        provisionSchool("demo")
        forgot("demo", "nobody@demo.com").andExpect { status { isAccepted() } }
        assertTrue(mailer.sent.isEmpty())

        forgot("demo", "admin@demo.com")
        val old = mailer.lastTokenFor("admin@demo.com")
        forgot("demo", "admin@demo.com")
        reset("demo", old).andExpect { status { isBadRequest() } }
        reset("demo", mailer.lastTokenFor("admin@demo.com")).andExpect { status { isNoContent() } }

        // Past 3 emails per hour the account gets nothing more, still with 202.
        repeat(2) { forgot("demo", "admin@demo.com").andExpect { status { isAccepted() } } }
        assertEquals(3, mailer.sent.size)
    }

    @Test
    fun `a link expires after one hour and only works in its own school`() {
        provisionSchool("demo")
        provisionSchool("other")
        forgot("demo", "admin@demo.com")
        val token = mailer.lastTokenFor("admin@demo.com")
        reset("other", token).andExpect { status { isBadRequest() } }

        clock.now = Instant.now().plus(Duration.ofMinutes(61))
        reset("demo", token).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `resetting clears a temporary password`() {
        provisionSchool("demo")
        createTeacher("demo", "t@demo.com") // created with mustChangePassword
        forgot("demo", "t@demo.com")
        reset("demo", mailer.lastTokenFor("t@demo.com")).andExpect { status { isNoContent() } }
        login("demo", "t@demo.com", "new-password-1").andExpect { jsonPath("$.user.mustChangePassword") { value(false) } }
    }

    @Test
    fun `the bootstrapped admin sets the first password with a reset link and uses the operator API`() {
        bootstrap.run(DefaultApplicationArguments())
        bootstrap.run(DefaultApplicationArguments()) // idempotent
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM platform_admins", Int::class.java))

        mockMvc.post("/api/platform/auth/login") { json("""{"email":"$admin","password":"anything"}""") }
            .andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/platform/auth/forgot-password") { json("""{"email":"$admin"}""") }.andExpect { status { isAccepted() } }
        val mail = mailer.sent.single { it.to == admin }
        assertTrue("http://localhost:3000/admin/reset-password?token=" in mail.text)
        mockMvc.post("/api/platform/auth/reset-password") { json("""{"token":"${mailer.lastTokenFor(admin)}","newPassword":"admin-pass-1"}""") }
            .andExpect { status { isNoContent() } }

        val body = mockMvc.post("/api/platform/auth/login") { json("""{"email":"$admin","password":"admin-pass-1"}""") }
            .andExpect {
                status { isOk() }
                jsonPath("$.admin.email") { value(admin) }
            }.andReturn().response.contentAsString
        val token = jsonMapper.readTree(body).get("token").asString()

        mockMvc.get("/api/platform/auth/me") { header("Authorization", "Bearer $token") }.andExpect { jsonPath("$.email") { value(admin) } }
        mockMvc.get("/api/platform/tenants") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }

        // An admin token is useless in a school, and a school token is useless on the platform API.
        provisionSchool("demo")
        mockMvc.get("/api/auth/me") { auth("demo", token) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/platform/tenants") { header("Authorization", "Bearer ${tokenFor("demo")}") }
            .andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/platform/tenants").andExpect { status { isUnauthorized() } }
    }
}
