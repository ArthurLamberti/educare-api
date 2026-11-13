package br.com.educare.api.student

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class StudentControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    var anaId = 0L
    var bobId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        provisionSchool("beta")
        admin = tokenFor("alpha")
        anaId = createTeacher("alpha", "ana@alpha.com")
        bobId = createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
    }

    private fun create(token: String, body: String) = mockMvc.post("/api/students") {
        auth("alpha", token)
        json(body)
    }

    private fun student(name: String, email: String, extra: String = "") =
        """{"name":"$name","email":"$email","birthDate":"2010-05-20","initialPassword":"initial-1"$extra}"""

    @Test
    fun `teacher creates a student account owned by them`() {
        create(ana, student("Maria", "maria@alpha.com")).andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.name") { value("Maria") }
            jsonPath("$.teacherId") { value(anaId) }
            jsonPath("$.active") { value(true) }
            jsonPath("$.initialPassword") { doesNotExist() }
        }
        login("alpha", "maria@alpha.com", "initial-1").andExpect {
            status { isOk() }
            jsonPath("$.user.role") { value("STUDENT") }
            jsonPath("$.user.mustChangePassword") { value(true) }
        }
    }

    @Test
    fun `rejects invalid data`() {
        create(ana, """{"name":"","email":"not-an-email","initialPassword":"short"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.name") { exists() }
            jsonPath("$.errors.email") { exists() }
            jsonPath("$.errors.initialPassword") { exists() }
        }
    }

    @Test
    fun `rejects an email used by any user of the school`() {
        create(ana, student("Maria", "maria@alpha.com")).andExpect { status { isCreated() } }
        create(bob, student("Maria 2", "MARIA@alpha.com")).andExpect { status { isConflict() } }
        create(ana, student("Teacher clash", "bob@alpha.com")).andExpect { status { isConflict() } }
    }

    @Test
    fun `admin must choose an active teacher`() {
        create(admin, student("Maria", "maria@alpha.com")).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.teacherId") { exists() }
        }
        mockMvc.post("/api/teachers/$bobId/deactivate") { auth("alpha", admin) }
        create(admin, student("Maria", "maria@alpha.com", ""","teacherId":$bobId""")).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.teacherId") { exists() }
        }
        create(admin, student("Maria", "maria@alpha.com", ""","teacherId":$anaId""")).andExpect {
            status { isCreated() }
            jsonPath("$.teacherId") { value(anaId) }
        }
    }

    @Test
    fun `teachers list only their students, admins list all`() {
        createStudent("alpha", ana, "zoe@alpha.com", name = "Zoe")
        createStudent("alpha", ana, "carl@alpha.com", name = "Carl")
        createStudent("alpha", bob, "dan@alpha.com", name = "Dan")

        mockMvc.get("/api/students") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.content[0].name") { value("Carl") }
            jsonPath("$.content[1].name") { value("Zoe") }
            jsonPath("$.totalElements") { value(2) }
        }
        // a teacher cannot widen the scope with teacherId
        mockMvc.get("/api/students?teacherId=$bobId") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content.length()") { value(2) }
        }
        mockMvc.get("/api/students") { auth("alpha", admin) }.andExpect {
            jsonPath("$.content.length()") { value(3) }
            jsonPath("$.content[0].teacherName") { exists() }
        }
        mockMvc.get("/api/students?teacherId=$bobId") { auth("alpha", admin) }.andExpect {
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].name") { value("Dan") }
        }
    }

    @Test
    fun `students are forbidden`() {
        createStudent("alpha", ana, "maria@alpha.com")
        mockMvc.get("/api/students") { auth("alpha", tokenFor("alpha", "maria@alpha.com")) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `reads a student, hiding other teachers' and schools' students`() {
        val id = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        mockMvc.get("/api/students/$id") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("maria@alpha.com") }
            jsonPath("$.birthDate") { isEmpty() }
            jsonPath("$.teacherName") { exists() }
            jsonPath("$.createdAt") { exists() }
        }
        mockMvc.get("/api/students/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/students/$id") { auth("alpha", admin) }.andExpect { status { isOk() } }
        mockMvc.get("/api/students/999") { auth("alpha", ana) }.andExpect { status { isNotFound() } }

        val betaTeacher = createTeacher("beta", "t@beta.com")
        mockMvc.get("/api/students/$id") { auth("beta", tokenFor("beta", "t@beta.com")) }
            .andExpect { status { isNotFound() } }
        check(betaTeacher > 0)
    }

    @Test
    fun `updates a student with email rules`() {
        val id = createStudent("alpha", ana, "maria@alpha.com")
        createStudent("alpha", ana, "joao@alpha.com")
        mockMvc.put("/api/students/$id") {
            auth("alpha", ana)
            json("""{"name":"Maria Silva","email":"maria.silva@alpha.com","birthDate":"2011-01-02"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Maria Silva") }
            jsonPath("$.email") { value("maria.silva@alpha.com") }
            jsonPath("$.birthDate") { value("2011-01-02") }
        }
        mockMvc.put("/api/students/$id") {
            auth("alpha", ana)
            json("""{"name":"Maria","email":"joao@alpha.com"}""")
        }.andExpect { status { isConflict() } }
        mockMvc.put("/api/students/$id") {
            auth("alpha", bob)
            json("""{"name":"Hack","email":"hack@alpha.com"}""")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `admin reassigns a student to another teacher`() {
        val id = createStudent("alpha", ana, "maria@alpha.com")
        mockMvc.put("/api/students/$id") {
            auth("alpha", admin)
            json("""{"name":"Maria","email":"maria@alpha.com","teacherId":$bobId}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.teacherId") { value(bobId) }
        }
        mockMvc.get("/api/students") { auth("alpha", ana) }.andExpect { jsonPath("$.content.length()") { value(0) } }
        mockMvc.get("/api/students") { auth("alpha", bob) }.andExpect { jsonPath("$.content.length()") { value(1) } }

        // a teacher's teacherId is ignored
        mockMvc.put("/api/students/$id") {
            auth("alpha", bob)
            json("""{"name":"Maria","email":"maria@alpha.com","teacherId":$anaId}""")
        }.andExpect { jsonPath("$.teacherId") { value(bobId) } }
    }

    @Test
    fun `delete deactivates and activate restores`() {
        val id = createStudent("alpha", ana, "maria@alpha.com")
        val studentToken = tokenFor("alpha", "maria@alpha.com")

        mockMvc.delete("/api/students/$id") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/students") { auth("alpha", ana) }.andExpect { jsonPath("$.content.length()") { value(0) } }
        mockMvc.get("/api/students?includeInactive=true") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].active") { value(false) }
        }
        mockMvc.get("/api/students/$id") { auth("alpha", ana) }.andExpect { jsonPath("$.active") { value(false) } }
        login("alpha", "maria@alpha.com").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/auth/me") { auth("alpha", studentToken) }.andExpect { status { isUnauthorized() } }

        mockMvc.post("/api/students/$id/activate") { auth("alpha", admin) }.andExpect { status { isNoContent() } }
        login("alpha", "maria@alpha.com").andExpect { status { isOk() } }
        mockMvc.delete("/api/students/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `new student logs in, changes the temporary password and the flag clears`() {
        createStudent("alpha", ana, "maria@alpha.com", password = "temporary-1")
        val token = tokenFor("alpha", "maria@alpha.com", "temporary-1")
        mockMvc.get("/api/auth/me") { auth("alpha", token) }.andExpect {
            jsonPath("$.role") { value("STUDENT") }
            jsonPath("$.mustChangePassword") { value(true) }
        }
        mockMvc.post("/api/auth/change-password") {
            auth("alpha", token)
            json("""{"currentPassword":"temporary-1","newPassword":"my-own-pass-1"}""")
        }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/auth/me") { auth("alpha", token) }.andExpect { jsonPath("$.mustChangePassword") { value(false) } }
    }
}
