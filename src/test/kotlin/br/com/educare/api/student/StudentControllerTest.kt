package br.com.educare.api.student

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import br.com.educare.api.TestcontainersConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StudentControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var repository: StudentRepository

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        repository.deleteAll()
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    private fun create(name: String, email: String) = mockMvc.post("/api/students") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"$name","email":"$email","birthDate":"2010-05-20"}"""
    }

    @Test
    fun `creates, gets, updates and deletes a student`() {
        create("Maria", "maria@educare.com").andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.name") { value("Maria") }
        }

        val id = repository.findAll().single().id

        mockMvc.get("/api/students/$id").andExpect {
            status { isOk() }
            jsonPath("$.email") { value("maria@educare.com") }
        }

        mockMvc.put("/api/students/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Maria Silva","email":"maria@educare.com"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Maria Silva") }
        }

        mockMvc.get("/api/students").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
        }

        mockMvc.delete("/api/students/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/students/$id").andExpect { status { isNotFound() } }
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
}
