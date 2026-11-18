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
            jsonPath("$.added[*].front") { value(contains("house", "cat")) }
            jsonPath("$.skipped", hasSize<Any>(0))
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
            """insert into cards (tenant_id, student_id, front, front_key, back, created_at, updated_at)
               select 1, ?, 'f' || g, 'f' || g, 'b' || g, now(), now() from generate_series(1, 1999) g""",
            mariaId,
        )
        addAll(mariaId, "a" to "1", "b" to "2").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A student can have at most 2000 cards; they have 1999, so at most 1 more can be added") }
        }
        // only the cards actually added count: a duplicate does not take the last place
        addAll(mariaId, "f1" to "1", "a" to "1").andExpect {
            status { isCreated() }
            jsonPath("$.skipped[0]") { value("f1") }
        }
        add(mariaId, "b").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A student can have at most 2000 cards") }
        }
    }

    @Test
    fun `a single card whose front the student already has is refused`() {
        add(mariaId, "Apple", "maçã").andExpect { status { isCreated() } }
        add(mariaId, "  apple ").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The student already has a card for \"apple\"") }
        }
        add(mariaId, "APPLE").andExpect { status { isConflict() } }
        // accents count, and another student is independent
        add(mariaId, "café").andExpect { status { isCreated() } }
        add(mariaId, "cafe").andExpect { status { isCreated() } }
        add(joaoId, "apple").andExpect { status { isCreated() } }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("Apple", "café", "cafe")) }
        }
    }

    @Test
    fun `inner spaces are collapsed when comparing fronts`() {
        add(mariaId, "ice cream").andExpect { status { isCreated() } }
        add(mariaId, "Ice   Cream").andExpect { status { isConflict() } }
    }

    @Test
    fun `a batch skips the duplicates and adds the rest`() {
        add(mariaId, "apple")
        addAll(mariaId, "Apple" to "1", "pear" to "2", " Pear " to "3", "plum" to "4").andExpect {
            status { isCreated() }
            jsonPath("$.added[*].front") { value(contains("pear", "plum")) }
            jsonPath("$.added[0].back") { value("2") }
            jsonPath("$.skipped") { value(contains("Apple", "Pear")) }
        }
        addAll(mariaId, "APPLE" to "1", "plum" to "2").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("All these cards are already in the student's flashcards") }
        }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("apple", "pear", "plum")) }
        }
    }

    @Test
    fun `editing a card into another card's front is refused, changing its own case is not`() {
        add(mariaId, "dog")
        val catId = id(add(mariaId, "cat").andReturn().response.contentAsString)
        mockMvc.put("/api/cards/$catId") {
            auth("alpha", ana)
            json("""{"front":"Dog","back":"x"}""")
        }.andExpect { status { isConflict() } }
        mockMvc.put("/api/cards/$catId") {
            auth("alpha", ana)
            json("""{"front":"Cat","back":"gato"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.front") { value("Cat") }
        }
        mockMvc.get("/api/students/$mariaId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("dog", "Cat")) }
        }
    }

    @Test
    fun `the database refuses a duplicate that slips past the check`() {
        add(mariaId, "dog")
        val error = runCatching {
            jdbc.update(
                "insert into cards (tenant_id, student_id, front, front_key, back, created_at, updated_at) select tenant_id, student_id, 'DOG', 'dog', 'x', now(), now() from cards where student_id = ?",
                mariaId,
            )
        }.exceptionOrNull()
        assert(error?.message.orEmpty().contains("ux_cards_student_front")) { "expected the unique index to refuse it, got $error" }
    }
}
