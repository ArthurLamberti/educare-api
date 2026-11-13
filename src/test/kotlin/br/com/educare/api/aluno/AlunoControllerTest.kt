package br.com.educare.api.aluno

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class AlunoControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var repository: AlunoRepository

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        repository.deleteAll()
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    private fun criar(nome: String, email: String) = mockMvc.post("/api/alunos") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"nome":"$nome","email":"$email","dataNascimento":"2010-05-20"}"""
    }

    @Test
    fun `cria, busca, atualiza e remove um aluno`() {
        criar("Maria", "maria@educare.com").andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.nome") { value("Maria") }
        }

        val id = repository.findAll().single().id

        mockMvc.get("/api/alunos/$id").andExpect {
            status { isOk() }
            jsonPath("$.email") { value("maria@educare.com") }
        }

        mockMvc.put("/api/alunos/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nome":"Maria Silva","email":"maria@educare.com"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nome") { value("Maria Silva") }
        }

        mockMvc.get("/api/alunos").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(1) }
        }

        mockMvc.delete("/api/alunos/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/alunos/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `retorna 400 para dados invalidos`() {
        criar("", "nao-e-email").andExpect {
            status { isBadRequest() }
            jsonPath("$.erros.nome") { exists() }
            jsonPath("$.erros.email") { exists() }
        }
    }

    @Test
    fun `retorna 409 para email duplicado`() {
        criar("Joao", "joao@educare.com").andExpect { status { isCreated() } }
        criar("Joao 2", "joao@educare.com").andExpect { status { isConflict() } }
    }
}
