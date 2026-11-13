package br.com.educare.api.signup

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignupControllerTest : IntegrationTest() {

    private fun signUp(slug: String = "ana-ingles", email: String = "ana@mail.com", ip: String = "203.0.113.1", password: String = PASSWORD) =
        mockMvc.post("/api/public/signups") {
            header("X-Forwarded-For", ip)
            json(
                """{"name":"Ana Souza","email":"$email","password":"$password","spaceName":"Ana – Inglês",
                   "slug":"$slug","timeZone":"America/Sao_Paulo"}""",
            )
        }

    private fun confirm(token: String) = mockMvc.post("/api/public/signups/confirm") { json("""{"token":"$token"}""") }

    private fun signUpAndConfirm(slug: String = "ana-ingles", email: String = "ana@mail.com") {
        signUp(slug, email).andExpect { status { isAccepted() } }
        confirm(mailer.lastTokenFor(email)).andExpect { status { isOk() } }
    }

    /** Signed up, confirmed and activated by the operator: ready to use. */
    private fun activeSpace(slug: String = "ana-ingles", email: String = "ana@mail.com") {
        signUpAndConfirm(slug, email)
        activate(slug)
    }

    @Test
    fun `address availability reports invalid, reserved, taken and free addresses`() {
        provisionSchool("demo")
        mapOf("Ab" to "INVALID", "www" to "RESERVED", "demo" to "TAKEN").forEach { (slug, reason) ->
            mockMvc.get("/api/public/slug-availability") { param("slug", slug) }.andExpect {
                status { isOk() }
                jsonPath("$.available") { value(false) }
                jsonPath("$.reason") { value(reason) }
            }
        }
        mockMvc.get("/api/public/slug-availability") { param("slug", "ana-ingles") }.andExpect {
            status { isOk() }
            jsonPath("$.available") { value(true) }
        }
    }

    @Test
    fun `sign-up creates an individual space and emails a link, and login waits for confirmation`() {
        signUp().andExpect {
            status { isAccepted() }
            jsonPath("$.slug") { value("ana-ingles") }
        }
        val mail = mailer.sent.single()
        assertEquals("ana@mail.com", mail.to)
        assertTrue("http://ana-ingles.localhost:3000/confirm?token=" in mail.text)

        login("ana-ingles", "ana@mail.com").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_CONFIRMED") }
        }
        login("ana-ingles", "ana@mail.com", "wrong-password").andExpect { status { isUnauthorized() } }

        confirm(mailer.lastTokenFor("ana@mail.com")).andExpect {
            status { isOk() }
            jsonPath("$.slug") { value("ana-ingles") }
        }
        val token = tokenFor("ana-ingles", "ana@mail.com")
        mockMvc.get("/api/auth/me") { auth("ana-ingles", token) }.andExpect {
            jsonPath("$.role") { value("TEACHER") }
            jsonPath("$.mustChangePassword") { value(false) }
            jsonPath("$.tenant.kind") { value("INDIVIDUAL") }
            jsonPath("$.tenant.name") { value("Ana – Inglês") }
            jsonPath("$.tenant.status") { value("PENDING") }
        }
        mockMvc.get("/api/tenant") { header("X-Tenant", "ana-ingles") }.andExpect { jsonPath("$.kind") { value("INDIVIDUAL") } }
    }

    @Test
    fun `sign-up refuses taken, reserved and invalid data and sends nothing`() {
        provisionSchool("demo")
        signUp(slug = "demo").andExpect { status { isConflict() } }
        signUp(slug = "www").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.slug") { value("is reserved") }
        }
        mockMvc.post("/api/public/signups") {
            json("""{"name":"","email":"nope","password":"short","spaceName":"X","slug":"-x","timeZone":"Mars/Base"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.name") { exists() }
            jsonPath("$.errors.email") { exists() }
            jsonPath("$.errors.password") { exists() }
            jsonPath("$.errors.slug") { exists() }
            jsonPath("$.errors.timeZone") { exists() }
        }
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `signing up again with the same password corrects an unconfirmed sign-up`() {
        signUp(email = "ana@mial.com").andExpect { status { isAccepted() } }
        val wrong = mailer.lastTokenFor("ana@mial.com")

        signUp(email = "ana@mail.com", password = "another-password").andExpect { status { isConflict() } }
        signUp(email = "ana@mail.com").andExpect { status { isAccepted() } }
        confirm(wrong).andExpect { status { isBadRequest() } }
        confirm(mailer.lastTokenFor("ana@mail.com")).andExpect { status { isOk() } }
        tokenFor("ana-ingles", "ana@mail.com")

        // Once confirmed, the address is taken for good.
        signUp(email = "other@mail.com").andExpect { status { isConflict() } }
    }

    @Test
    fun `a confirmation link works once and expires after 48 hours`() {
        signUp().andExpect { status { isAccepted() } }
        val token = mailer.lastTokenFor("ana@mail.com")
        confirm(token).andExpect { status { isOk() } }
        confirm(token).andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("This confirmation link is invalid or has expired") }
        }
        confirm("not-a-token").andExpect { status { isBadRequest() } }

        signUp(slug = "bia-frances", email = "bia@mail.com").andExpect { status { isAccepted() } }
        clock.now = Instant.now().plus(Duration.ofHours(49))
        confirm(mailer.lastTokenFor("bia@mail.com")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `resend sends a new link, invalidates the old one and never reveals accounts`() {
        signUp().andExpect { status { isAccepted() } }
        val old = mailer.lastTokenFor("ana@mail.com")

        mockMvc.post("/api/public/signups/resend") { json("""{"email":"ANA@mail.com","slug":"ana-ingles"}""") }
            .andExpect { status { isAccepted() } }
        val new = mailer.lastTokenFor("ana@mail.com")
        assertEquals(2, mailer.sent.count { it.to == "ana@mail.com" })
        confirm(old).andExpect { status { isBadRequest() } }
        confirm(new).andExpect { status { isOk() } }

        // Confirmed account, unknown email, school tenant: same answer, no email.
        provisionSchool("demo")
        listOf("ana@mail.com" to "ana-ingles", "nobody@mail.com" to "ana-ingles", "admin@demo.com" to "demo").forEach { (email, slug) ->
            mockMvc.post("/api/public/signups/resend") { json("""{"email":"$email","slug":"$slug"}""") }
                .andExpect { status { isAccepted() } }
        }
        assertEquals(2, mailer.sent.count { it.to == "ana@mail.com" })
        assertTrue(mailer.sent.none { it.to == "admin@demo.com" || it.to == "nobody@mail.com" })
    }

    @Test
    fun `sign-ups are rate limited per client IP`() {
        repeat(5) { i -> signUp(slug = "space-$i", email = "t$i@mail.com").andExpect { status { isAccepted() } } }
        signUp(slug = "space-5", email = "t5@mail.com").andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
        }
        // Another browser behind the same web server is counted apart.
        signUp(slug = "space-6", email = "t6@mail.com", ip = "203.0.113.2").andExpect { status { isAccepted() } }
    }

    @Test
    fun `unconfirmed sign-ups are deleted after 7 days and free the address`() {
        signUpAndConfirm("ana-ingles", "ana@mail.com")
        signUp(slug = "bia-frances", email = "bia@mail.com").andExpect { status { isAccepted() } }

        clock.now = Instant.now().plus(Duration.ofDays(6))
        assertEquals(0, signupService.deleteExpiredSignups())
        clock.now = Instant.now().plus(Duration.ofDays(8))
        assertEquals(1, signupService.deleteExpiredSignups())

        mockMvc.get("/api/public/slug-availability") { param("slug", "bia-frances") }
            .andExpect { jsonPath("$.available") { value(true) } }
        mockMvc.get("/api/tenant") { header("X-Tenant", "bia-frances") }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/tenant") { header("X-Tenant", "ana-ingles") }.andExpect { status { isOk() } }
    }

    @Test
    fun `a private teacher works like any teacher but cannot manage teachers`() {
        activeSpace()
        val token = tokenFor("ana-ingles", "ana@mail.com")
        val studentId = createStudent("ana-ingles", token, "maria@mail.com")

        mockMvc.post("/api/lessons") {
            auth("ana-ingles", token)
            json("""{"studentId":$studentId,"startsAt":"${Instant.now().plus(Duration.ofDays(2))}","durationMinutes":60,"topic":"Intro"}""")
        }.andExpect { status { isCreated() } }
        mockMvc.post("/api/students/$studentId/cards") {
            auth("ana-ingles", token)
            json("""{"front":"cat","back":"gato"}""")
        }.andExpect { status { isCreated() } }
        mockMvc.get("/api/teachers") { auth("ana-ingles", token) }.andExpect { status { isForbidden() } }
        tokenFor("ana-ingles", "maria@mail.com")
    }

    @Test
    fun `settings can be changed by a private teacher and a school admin only`() {
        activeSpace()
        val teacher = tokenFor("ana-ingles", "ana@mail.com")
        mockMvc.put("/api/tenant") {
            auth("ana-ingles", teacher)
            json("""{"name":"Ana English","timeZone":"America/Manaus"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Ana English") }
            jsonPath("$.timeZone") { value("America/Manaus") }
        }
        mockMvc.get("/api/tenant") { header("X-Tenant", "ana-ingles") }.andExpect { jsonPath("$.name") { value("Ana English") } }

        provisionSchool("demo")
        val admin = tokenFor("demo")
        val schoolTeacher = run { createTeacher("demo", "t@demo.com"); tokenFor("demo", "t@demo.com") }
        mockMvc.put("/api/tenant") {
            auth("demo", schoolTeacher)
            json("""{"name":"Mine","timeZone":"UTC"}""")
        }.andExpect { status { isForbidden() } }
        mockMvc.put("/api/tenant") {
            auth("demo", admin)
            json("""{"name":"Demo Academy","timeZone":"Mars/Base"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.timeZone") { exists() }
        }
        mockMvc.put("/api/tenant") {
            auth("demo", admin)
            json("""{"name":"Demo Academy","timeZone":"Europe/Lisbon"}""")
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/auth/me") { auth("demo", admin) }.andExpect {
            jsonPath("$.tenant.name") { value("Demo Academy") }
            jsonPath("$.tenant.kind") { value("SCHOOL") }
        }
    }

    @Test
    fun `the operator cannot provision a reserved address`() {
        mockMvc.post("/api/platform/tenants") {
            header("X-Platform-Key", "dev-platform-key")
            json("""{"slug":"www","name":"W","timeZone":"UTC","admin":{"name":"A","email":"a@w.com","password":"$PASSWORD"}}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.slug") { value(containsString("reserved")) }
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var signupService: SignupService
}
