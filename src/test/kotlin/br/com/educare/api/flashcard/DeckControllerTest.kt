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

class DeckControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var mariaId = 0L
    var joaoId = 0L
    var carlaId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        admin = tokenFor("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        joaoId = createStudent("alpha", ana, "joao@alpha.com", name = "Joao")
        carlaId = createStudent("alpha", bob, "carla@alpha.com", name = "Carla")
        maria = tokenFor("alpha", "maria@alpha.com")
        joao = tokenFor("alpha", "joao@alpha.com")
    }

    private fun id(body: String) = jsonMapper.readTree(body).get("id")?.asLong() ?: error("No id: $body")

    private fun deck(token: String = ana, title: String = "Animals"): Long = id(
        mockMvc.post("/api/decks") {
            auth("alpha", token)
            json("""{"title":"$title","description":"Common animals"}""")
        }.andReturn().response.contentAsString,
    )

    private fun addCard(deckId: Long, front: String, back: String = "$front-back", token: String = ana) =
        mockMvc.post("/api/decks/$deckId/cards") {
            auth("alpha", token)
            json("""{"front":"$front","back":"$back"}""")
        }

    private fun card(deckId: Long, front: String) = id(addCard(deckId, front).andReturn().response.contentAsString)

    private fun propose(deckId: Long, vararg studentIds: Long, token: String = ana) = mockMvc.post("/api/decks/$deckId/students") {
        auth("alpha", token)
        json("""{"studentIds":${studentIds.joinToString(",", "[", "]")}}""")
    }

    private fun order(deckId: Long, vararg ids: Long) = mockMvc.put("/api/decks/$deckId/cards/order") {
        auth("alpha", ana)
        json("""{"cardIds":${ids.joinToString(",", "[", "]")}}""")
    }

    @Test
    fun `teacher creates, updates and reads a deck`() {
        mockMvc.post("/api/decks") {
            auth("alpha", ana)
            json("""{"title":" Animals ","description":" "}""")
        }.andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.title") { value("Animals") }
            jsonPath("$.description") { value(null as Any?) }
            jsonPath("$.cardCount") { value(0) }
            jsonPath("$.studentCount") { value(0) }
            jsonPath("$.teacherName") { value("Teacher ana@alpha.com") }
        }
        mockMvc.put("/api/decks/1") {
            auth("alpha", ana)
            json("""{"title":"Farm animals","description":"Cow, pig"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Farm animals") }
        }
        mockMvc.post("/api/decks") {
            auth("alpha", ana)
            json("""{"title":""}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { exists() }
        }
    }

    @Test
    fun `another teacher's deck is not found`() {
        val deckId = deck(ana)
        mockMvc.get("/api/decks/$deckId") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/decks/$deckId/cards") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        addCard(deckId, "cat", token = bob).andExpect { status { isNotFound() } }
        mockMvc.get("/api/decks") { auth("alpha", bob) }.andExpect { jsonPath("$.totalElements") { value(0) } }
    }

    @Test
    fun `archived decks are hidden by default`() {
        val old = deck(title = "Old")
        deck(title = "New")
        mockMvc.post("/api/decks/$old/archive") { auth("alpha", ana) }.andExpect { jsonPath("$.archived") { value(true) } }
        mockMvc.get("/api/decks") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("New")) }
        }
        mockMvc.get("/api/decks?includeArchived=true") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("New", "Old")) }
        }
        mockMvc.post("/api/decks/$old/unarchive") { auth("alpha", ana) }.andExpect { jsonPath("$.archived") { value(false) } }
    }

    @Test
    fun `cards are appended last and returned in order`() {
        val deckId = deck()
        card(deckId, "cat")
        addCard(deckId, " dog ", " cachorro ").andExpect {
            status { isCreated() }
            jsonPath("$.front") { value("dog") }
            jsonPath("$.back") { value("cachorro") }
            jsonPath("$.position") { value(1) }
        }
        mockMvc.get("/api/decks/$deckId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("cat", "dog")) }
        }
        mockMvc.get("/api/decks/$deckId") { auth("alpha", ana) }.andExpect { jsonPath("$.cardCount") { value(2) } }
    }

    @Test
    fun `card sides are required`() {
        val deckId = deck()
        addCard(deckId, "cat", " ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.back") { exists() }
        }
        addCard(deckId, "x".repeat(1001)).andExpect { jsonPath("$.errors.front") { exists() } }
    }

    @Test
    fun `teacher edits and deletes cards, keeping ids`() {
        val deckId = deck()
        val cat = card(deckId, "cat")
        val dog = card(deckId, "dog")
        mockMvc.put("/api/cards/$cat") {
            auth("alpha", ana)
            json("""{"front":"cat","back":"gato"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cat) }
            jsonPath("$.back") { value("gato") }
        }
        mockMvc.put("/api/cards/$cat") {
            auth("alpha", bob)
            json("""{"front":"x","back":"y"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/cards/$dog") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/decks/$deckId/cards") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
    }

    @Test
    fun `reorder needs exactly the deck's cards`() {
        val deckId = deck()
        val a = card(deckId, "a")
        val b = card(deckId, "b")
        val c = card(deckId, "c")
        val foreign = card(deck(title = "Other"), "z")

        order(deckId, c, a, b).andExpect {
            status { isOk() }
            jsonPath("$[*].front") { value(contains("c", "a", "b")) }
            jsonPath("$[*].position") { value(contains(0, 1, 2)) }
        }
        order(deckId, a, b).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.cardIds") { exists() }
        }
        order(deckId, a, b, foreign).andExpect { status { isBadRequest() } }
        order(deckId, a, a, b).andExpect { status { isBadRequest() } }
        mockMvc.get("/api/decks/$deckId/cards") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].front") { value(contains("c", "a", "b")) }
        }
        // a new card still goes last after a reorder
        addCard(deckId, "d").andExpect { jsonPath("$.position") { value(3) } }
    }

    @Test
    fun `a deck holds at most 1000 cards`() {
        val deckId = deck()
        jdbc.update(
            """insert into cards (tenant_id, deck_id, front, back, position, created_at, updated_at)
               select 1, ?, 'f' || g, 'b' || g, g, now(), now() from generate_series(0, 999) g""",
            deckId,
        )
        addCard(deckId, "one more").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A deck can hold at most 1000 cards") }
        }
    }

    @Test
    fun `teacher proposes a deck to students, idempotently`() {
        val deckId = deck()
        propose(deckId, mariaId, joaoId).andExpect {
            status { isOk() }
            jsonPath("$[*].name") { value(contains("Joao", "Maria")) }
        }
        propose(deckId, mariaId).andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
        }
        mockMvc.get("/api/decks/$deckId") { auth("alpha", ana) }.andExpect { jsonPath("$.studentCount") { value(2) } }
        mockMvc.get("/api/decks") { auth("alpha", maria) }.andExpect { jsonPath("$.totalElements") { value(1) } }
        mockMvc.get("/api/decks") { auth("alpha", joao) }.andExpect { jsonPath("$.totalElements") { value(1) } }
    }

    @Test
    fun `student of another teacher is rejected and nothing is proposed`() {
        val deckId = deck()
        propose(deckId, mariaId, carlaId).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.studentIds") { exists() }
        }
        mockMvc.get("/api/decks/$deckId/students") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
    }

    @Test
    fun `teacher removes a deck from a student`() {
        val deckId = deck()
        propose(deckId, mariaId, joaoId)
        mockMvc.delete("/api/decks/$deckId/students/$mariaId") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/decks") { auth("alpha", maria) }.andExpect { jsonPath("$.totalElements") { value(0) } }
        mockMvc.get("/api/decks/$deckId") { auth("alpha", maria) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/decks/$deckId/students") { auth("alpha", ana) }.andExpect {
            jsonPath("$[*].name") { value(contains("Joao")) }
        }
    }

    @Test
    fun `archived deck cannot be proposed but students keep it`() {
        val deckId = deck()
        propose(deckId, mariaId)
        mockMvc.post("/api/decks/$deckId/archive") { auth("alpha", ana) }
        propose(deckId, joaoId).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Archived decks cannot be proposed to students") }
        }
        mockMvc.get("/api/decks") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[0].archived") { value(true) }
        }
    }

    @Test
    fun `students read only decks proposed to them and cannot write`() {
        val mine = deck(title = "Animals")
        deck(title = "Not proposed")
        card(mine, "cat")
        propose(mine, mariaId)

        mockMvc.get("/api/decks") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("Animals")) }
            jsonPath("$.content[0].description") { value("Common animals") }
            jsonPath("$.content[0].teacherName") { value("Teacher ana@alpha.com") }
            jsonPath("$.content[0].cardCount") { value(1) }
            jsonPath("$.content[0].studentCount") { value(null as Any?) }
        }
        mockMvc.get("/api/decks/$mine/cards") { auth("alpha", maria) }.andExpect {
            jsonPath("$[*].front") { value(contains("cat")) }
        }
        mockMvc.get("/api/decks/$mine/cards") { auth("alpha", joao) }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/decks") {
            auth("alpha", maria)
            json("""{"title":"x"}""")
        }.andExpect { status { isForbidden() } }
        addCard(mine, "dog", token = maria).andExpect { status { isForbidden() } }
        mockMvc.get("/api/decks/$mine/students") { auth("alpha", maria) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/decks") { auth("alpha", admin) }.andExpect { jsonPath("$.totalElements") { value(2) } }
    }
}
