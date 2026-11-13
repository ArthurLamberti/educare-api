package br.com.educare.api.student

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class StudentControllerTest : IntegrationTest() {

    @Autowired
    lateinit var repository: StudentRepository

    lateinit var token: String

    @BeforeEach
    fun loginAsTeacher() {
        provisionSchool("alpha")
        createTeacher("alpha", "teacher@alpha.com")
        token = tokenFor("alpha", "teacher@alpha.com")
    }

    private fun create(name: String, email: String) = mockMvc.post("/api/students") {
        auth("alpha", token)
        json("""{"name":"$name","email":"$email","birthDate":"2010-05-20"}""")
    }

    @Test
    fun `creates, gets, updates and deletes a student`() {
        create("Maria", "maria@educare.com").andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.name") { value("Maria") }
        }

        val id = repository.findAll().single().id

        mockMvc.get("/api/students/$id") { auth("alpha", token) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("maria@educare.com") }
        }

        mockMvc.put("/api/students/$id") {
            auth("alpha", token)
            json("""{"name":"Maria Silva","email":"maria@educare.com"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Maria Silva") }
        }

        mockMvc.get("/api/students") { auth("alpha", token) }.andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
        }

        mockMvc.delete("/api/students/$id") { auth("alpha", token) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/students/$id") { auth("alpha", token) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `returns 400 for invalid data`() {
        create("", "not-an-email").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.name") { exists() }
            jsonPath("$.errors.email") { exists() }
        }
    }

    @Test
    fun `returns 409 for duplicate email`() {
        create("Joao", "joao@educare.com").andExpect { status { isCreated() } }
        create("Joao 2", "joao@educare.com").andExpect { status { isConflict() } }
    }

    @Test
    fun `requires authentication`() {
        mockMvc.get("/api/students") { header("X-Tenant", "alpha") }.andExpect { status { isUnauthorized() } }
    }
}
