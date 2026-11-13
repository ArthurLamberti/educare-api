package br.com.educare.api.auth

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

class AuthControllerTest : IntegrationTest() {

    @Autowired lateinit var encoder: JwtEncoder

    var alphaId = 0L

    @BeforeEach
    fun schools() {
        alphaId = provisionSchool("alpha").id
        provisionSchool("beta")
    }

    @Test
    fun `logs in with valid credentials, case-insensitive email`() {
        login("alpha", "ADMIN@alpha.com").andExpect {
            status { isOk() }
            jsonPath("$.token") { isNotEmpty() }
            jsonPath("$.expiresAt") { exists() }
            jsonPath("$.user.email") { value("admin@alpha.com") }
            jsonPath("$.user.role") { value("ADMIN") }
            jsonPath("$.user.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `rejects wrong password, unknown email and other school with the same message`() {
        listOf(
            login("alpha", "admin@alpha.com", "wrong-password"),
            login("alpha", "nobody@alpha.com"),
            login("beta", "admin@alpha.com"),
        ).forEach {
            it.andExpect {
                status { isUnauthorized() }
                jsonPath("$.detail") { value("Invalid email or password") }
            }
        }
    }

    @Test
    fun `rejects deactivated user`() {
        val teacherId = createTeacher("alpha", "t@alpha.com")
        mockMvc.post("/api/teachers/$teacherId/deactivate") { auth("alpha", tokenFor("alpha")) }
        login("alpha", "t@alpha.com").andExpect {
            status { isUnauthorized() }
            jsonPath("$.detail") { value("Invalid email or password") }
        }
    }

    @Test
    fun `requires a valid token`() {
        mockMvc.get("/api/auth/me") { header("X-Tenant", "alpha") }.andExpect { status { isUnauthorized() } }

        val token = tokenFor("alpha")
        val tampered = token.dropLast(2) + if (token.endsWith("AA")) "BB" else "AA"
        mockMvc.get("/api/auth/me") { auth("alpha", tampered) }.andExpect { status { isUnauthorized() } }

        val past = Instant.now().minus(Duration.ofHours(13))
        val expired = encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("1").claim("tid", alphaId).claim("role", "ADMIN")
                    .issuedAt(past).expiresAt(past.plus(Duration.ofHours(12))).build(),
            ),
        ).tokenValue
        mockMvc.get("/api/auth/me") { auth("alpha", expired) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `rejects a token used with another school`() {
        mockMvc.get("/api/auth/me") { auth("beta", tokenFor("alpha")) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `rejects token of a user deactivated after login`() {
        val teacherId = createTeacher("alpha", "t@alpha.com")
        val teacherToken = tokenFor("alpha", "t@alpha.com")
        mockMvc.get("/api/auth/me") { auth("alpha", teacherToken) }.andExpect { status { isOk() } }

        mockMvc.post("/api/teachers/$teacherId/deactivate") { auth("alpha", tokenFor("alpha")) }
            .andExpect { status { isNoContent() } }

        mockMvc.get("/api/auth/me") { auth("alpha", teacherToken) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `returns the current user with the school`() {
        mockMvc.get("/api/auth/me") { auth("alpha", tokenFor("alpha")) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("admin@alpha.com") }
            jsonPath("$.tenant.slug") { value("alpha") }
            jsonPath("$.tenant.name") { value("School alpha") }
            jsonPath("$.tenant.timeZone") { value("America/Sao_Paulo") }
            jsonPath("$.passwordHash") { doesNotExist() }
        }
    }

    @Test
    fun `forbids roles that are not allowed`() {
        createTeacher("alpha", "t@alpha.com")
        createStudent("alpha", tokenFor("alpha", "t@alpha.com"), "sam@alpha.com")
        val studentToken = tokenFor("alpha", "sam@alpha.com")
        mockMvc.get("/api/teachers") { auth("alpha", studentToken) }.andExpect {
            status { isForbidden() }
            jsonPath("$.status") { value(403) }
        }
        mockMvc.get("/api/students") { auth("alpha", studentToken) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `changes password and clears the forced change flag`() {
        createTeacher("alpha", "t@alpha.com", "temporary1")
        val token = tokenFor("alpha", "t@alpha.com", "temporary1")
        mockMvc.get("/api/auth/me") { auth("alpha", token) }.andExpect { jsonPath("$.mustChangePassword") { value(true) } }

        mockMvc.post("/api/auth/change-password") {
            auth("alpha", token)
            json("""{"currentPassword":"temporary1","newPassword":"brand-new-1"}""")
        }.andExpect { status { isNoContent() } }

        login("alpha", "t@alpha.com", "temporary1").andExpect { status { isUnauthorized() } }
        login("alpha", "t@alpha.com", "brand-new-1").andExpect {
            status { isOk() }
            jsonPath("$.user.mustChangePassword") { value(false) }
        }
    }

    @Test
    fun `rejects wrong current password and short new password`() {
        val token = tokenFor("alpha")
        mockMvc.post("/api/auth/change-password") {
            auth("alpha", token)
            json("""{"currentPassword":"wrong","newPassword":"brand-new-1"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.currentPassword") { exists() }
        }
        mockMvc.post("/api/auth/change-password") {
            auth("alpha", token)
            json("""{"currentPassword":"$PASSWORD","newPassword":"short"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.newPassword") { exists() }
        }
        login("alpha", "admin@alpha.com").andExpect { status { isOk() } }
    }
}
