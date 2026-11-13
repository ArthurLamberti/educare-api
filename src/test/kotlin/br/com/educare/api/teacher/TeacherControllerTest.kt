package br.com.educare.api.teacher

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class TeacherControllerTest : IntegrationTest() {

    lateinit var admin: String

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        provisionSchool("beta")
        admin = tokenFor("alpha")
    }

    private fun create(email: String, password: String = "initial-1") = mockMvc.post("/api/teachers") {
        auth("alpha", admin)
        json("""{"name":"Teacher","email":"$email","initialPassword":"$password"}""")
    }

    @Test
    fun `creates a teacher who must change password on first login`() {
        create("ana@alpha.com").andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.mustChangePassword") { value(true) }
        }
        login("alpha", "ana@alpha.com", "initial-1").andExpect {
            status { isOk() }
            jsonPath("$.user.role") { value("TEACHER") }
            jsonPath("$.user.mustChangePassword") { value(true) }
        }
    }

    @Test
    fun `rejects invalid data and duplicate email within the school`() {
        create("not-an-email", "short").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.email") { exists() }
            jsonPath("$.errors.initialPassword") { exists() }
        }
        create("ana@alpha.com").andExpect { status { isCreated() } }
        create("ANA@alpha.com").andExpect { status { isConflict() } }
        create("admin@alpha.com").andExpect { status { isConflict() } }
    }

    @Test
    fun `only admins manage teachers`() {
        create("ana@alpha.com")
        val teacher = tokenFor("alpha", "ana@alpha.com", "initial-1")
        mockMvc.post("/api/teachers") {
            auth("alpha", teacher)
            json("""{"name":"X","email":"x@alpha.com","initialPassword":"initial-1"}""")
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/teachers") { auth("alpha", teacher) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `lists only the school's teachers sorted by name`() {
        mockMvc.post("/api/teachers") { auth("alpha", admin); json("""{"name":"Zoe","email":"zoe@alpha.com","initialPassword":"initial-1"}""") }
        mockMvc.post("/api/teachers") { auth("alpha", admin); json("""{"name":"Bob","email":"bob@alpha.com","initialPassword":"initial-1"}""") }
        mockMvc.post("/api/teachers") { auth("beta", tokenFor("beta")); json("""{"name":"Other","email":"o@beta.com","initialPassword":"initial-1"}""") }

        mockMvc.get("/api/teachers") { auth("alpha", admin) }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.content[0].name") { value("Bob") }
            jsonPath("$.content[1].name") { value("Zoe") }
        }
    }

    @Test
    fun `updates a teacher`() {
        val id = createTeacher("alpha", "ana@alpha.com")
        mockMvc.put("/api/teachers/$id") {
            auth("alpha", admin)
            json("""{"name":"Ana Souza","email":"ana.souza@alpha.com"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Ana Souza") }
            jsonPath("$.email") { value("ana.souza@alpha.com") }
        }
    }

    @Test
    fun `deactivates and reactivates a teacher`() {
        val id = createTeacher("alpha", "ana@alpha.com")
        mockMvc.post("/api/teachers/$id/deactivate") { auth("alpha", admin) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/teachers/$id") { auth("alpha", admin) }.andExpect { jsonPath("$.active") { value(false) } }
        login("alpha", "ana@alpha.com").andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/teachers/$id/activate") { auth("alpha", admin) }.andExpect { status { isNoContent() } }
        login("alpha", "ana@alpha.com").andExpect { status { isOk() } }
    }

    @Test
    fun `another school's teacher is not found`() {
        val betaTeacher = createTeacher("beta", "t@beta.com")
        mockMvc.get("/api/teachers/$betaTeacher") { auth("alpha", admin) }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/teachers/$betaTeacher/deactivate") { auth("alpha", admin) }.andExpect { status { isNotFound() } }
    }
}
