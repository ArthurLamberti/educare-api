package br.com.educare.api.support

import br.com.educare.api.TestcontainersConfiguration
import br.com.educare.api.platform.AdminRequest
import br.com.educare.api.platform.PlatformService
import br.com.educare.api.platform.ProvisionTenantRequest
import br.com.educare.api.tenant.TenantDirectory
import br.com.educare.api.user.UserStatusCache
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper

/** Base for API tests: real PostgreSQL, Spring Security, clean database and helpers per test. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, ClockTestConfiguration::class)
@ActiveProfiles("test")
abstract class IntegrationTest {

    @Autowired lateinit var context: WebApplicationContext
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var jsonMapper: JsonMapper
    @Autowired lateinit var platform: PlatformService
    @Autowired lateinit var tenantDirectory: TenantDirectory
    @Autowired lateinit var userStatus: UserStatusCache
    @Autowired lateinit var clock: MutableClock

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun resetState() {
        jdbc.execute("TRUNCATE reschedule_requests, lessons, students, users, tenants RESTART IDENTITY CASCADE")
        tenantDirectory.clear()
        userStatus.clear()
        clock.now = null
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
    }

    /** Creates a school with admin `admin@<slug>.com` / [PASSWORD] (no forced password change). */
    fun provisionSchool(slug: String) = platform.provision(
        ProvisionTenantRequest(slug, "School $slug", "America/Sao_Paulo", AdminRequest("Admin $slug", "admin@$slug.com", PASSWORD)),
        adminMustChangePassword = false,
    )

    fun login(slug: String, email: String, password: String = PASSWORD) = mockMvc.post("/api/auth/login") {
        header("X-Tenant", slug)
        json("""{"email":"$email","password":"$password"}""")
    }

    fun tokenFor(slug: String, email: String = "admin@$slug.com", password: String = PASSWORD): String {
        val body = login(slug, email, password).andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("token")?.asString() ?: error("Login failed: $body")
    }

    /** Creates a teacher through the API as the school's admin and returns its id. */
    fun createTeacher(slug: String, email: String, password: String = PASSWORD): Long {
        val body = mockMvc.post("/api/teachers") {
            auth(slug, tokenFor(slug))
            json("""{"name":"Teacher $email","email":"$email","initialPassword":"$password"}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Teacher creation failed: $body")
    }

    /** Creates a student through the API as [teacherToken]'s user (a teacher) and returns its id. */
    fun createStudent(slug: String, teacherToken: String, email: String, password: String = PASSWORD, name: String = "Student $email"): Long {
        val body = mockMvc.post("/api/students") {
            auth(slug, teacherToken)
            json("""{"name":"$name","email":"$email","initialPassword":"$password"}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Student creation failed: $body")
    }

    companion object {
        const val PASSWORD = "password123"
    }
}

fun MockHttpServletRequestDsl.auth(slug: String, token: String) {
    header("X-Tenant", slug)
    header("Authorization", "Bearer $token")
}

fun MockHttpServletRequestDsl.json(body: String) {
    contentType = MediaType.APPLICATION_JSON
    content = body
}
