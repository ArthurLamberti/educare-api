package br.com.educare.api.flashcard

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class FlashcardControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    var mariaId = 0L
    var joaoId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        admin = tokenFor("alpha", "admin@alpha.com")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        joaoId = createStudent("alpha", ana, "joao@alpha.com", name = "Joao")
        maria = tokenFor("alpha", "maria@alpha.com")
    }

    private fun add(studentId: Long, front: String, back: String = "$front-back", token: String = ana) =
        mockMvc.post("/api/students/$studentId/cards") {
            auth("alpha", token)
            json("""{"front":"$front","back":"$back"}""")
        }

    private fun addAll(studentId: Long, vararg pairs: Pair<String, String>, token: String = ana) =
        mockMvc.post("/api/students/$studentId/cards/batch") {
            auth("alpha", token)
            json(pairs.joinToString(",", """{"cards":[""", "]}") { (front, back) -> """{"front":"$front","back":"$back"}""" })
        }

    private fun id(body: String) = jsonMapper.readTree(body).get("id").asLong()

    @Test
    fun `teacher adds cards to a student, in order, trimmed and new`() {
        add(mariaId, " dog ", " cachorro ").andExpect {
            status { isCreated() }
            jsonPath("$.studentId") { value(mariaId) }
            jsonPath("$.front") { value("dog") }
            jsonPath("$.back") { value("cachorro") }
            jsonPath("$.isNew") { value(true) }
            jsonPath("$.dueAt") { value(null as Any?) }
        }
        addAll(mariaId, "house" to "casa", "cat" to "gato").andExpect {
            status { isCreated() }
            jsonPath("$[*].front") { value(contains("house", "cat")) }
        }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("dog", "house", "cat")) }
        }
        // cards belong to one student only
        mockMvc.get("/api/students/$joaoId/cards") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
    }

    @Test
    fun `student, their teacher and admins read the cards, nobody else`() {
        add(mariaId, "dog")
        mockMvc.get("/api/cards") { auth("alpha", maria) }.andExpect { jsonPath("$[*].front") { value(contains("dog")) } }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", maria) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", admin) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/students/$joaoId/cards") { auth("alpha", maria) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `only the student's teacher writes cards`() {
        val cardId = id(add(mariaId, "dog").andReturn().response.contentAsString)
        add(mariaId, "cat", token = bob).andExpect { status { isNotFound() } }
        add(mariaId, "cat", token = maria).andExpect { status { isForbidden() } }
        add(mariaId, "cat", token = admin).andExpect { status { isForbidden() } }
        mockMvc.put("/api/cards/$cardId") {
            auth("alpha", bob)
            json("""{"front":"x","back":"y"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/cards/$cardId") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `editing keeps the id and the review progress`() {
        val cardId = id(add(mariaId, "dgo", "cachorro").andReturn().response.contentAsString)
        mockMvc.post("/api/reviews") {
            auth("alpha", maria)
            json("""{"cardId":$cardId,"rating":"GOOD"}""")
        }.andExpect { status { isOk() } }
        mockMvc.put("/api/cards/$cardId") {
            auth("alpha", ana)
            json("""{"front":"dog","back":"cachorro"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cardId) }
            jsonPath("$.front") { value("dog") }
            jsonPath("$.isNew") { value(false) }
            jsonPath("$.intervalDays") { value(1) }
        }
        mockMvc.delete("/api/cards/$cardId") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
    }

    @Test
    fun `card sides are required and a batch is all or nothing`() {
        add(mariaId, "cat", " ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.back") { exists() }
        }
        add(mariaId, "x".repeat(1001)).andExpect { jsonPath("$.errors.front") { exists() } }
        addAll(mariaId, "dog" to "cachorro", "house" to " ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors['cards[1].back']") { exists() }
        }
        mockMvc.post("/api/students/$mariaId/cards/batch") {
            auth("alpha", ana)
            json("""{"cards":[]}""")
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
    }

    @Test
    fun `a student has at most 2000 cards`() {
        jdbc.update(
            """insert into cards (tenant_id, student_id, front, back, created_at, updated_at)
               select 1, ?, 'f' || g, 'b' || g, now(), now() from generate_series(1, 1999) g""",
            mariaId,
        )
        addAll(mariaId, "a" to "1", "b" to "2").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A student can have at most 2000 cards; they have 1999, so at most 1 more can be added") }
        }
        add(mariaId, "a").andExpect { status { isCreated() } }
        add(mariaId, "b").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A student can have at most 2000 cards") }
        }
    }
}
