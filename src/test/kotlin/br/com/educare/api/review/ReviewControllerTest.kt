package br.com.educare.api.review

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
import java.time.Duration
import java.time.Instant

class ReviewControllerTest : IntegrationTest() {

    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var mariaId = 0L
    var joaoId = 0L

    /** Nov 13, 2026, 15:00 in São Paulo (the test schools' zone). */
    val day1: Instant = Instant.parse("2026-11-13T18:00:00Z")
    val day2: Instant = day1.plus(Duration.ofDays(1))
    /** Start of Nov 14 in São Paulo. */
    val startOfDay2: Instant = Instant.parse("2026-11-14T03:00:00Z")

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        joaoId = createStudent("alpha", ana, "joao@alpha.com", name = "Joao")
        maria = tokenFor("alpha", "maria@alpha.com")
        joao = tokenFor("alpha", "joao@alpha.com")
        clock.now = day1
    }

    /** A deck of [cards] cards (fronts "c0", "c1", ...) proposed to [students]; returns deck id and card ids in order. */
    private fun deck(cards: Int, vararg students: Long, title: String = "Animals"): Pair<Long, List<Long>> {
        val body = mockMvc.post("/api/decks") {
            auth("alpha", ana)
            json("""{"title":"$title"}""")
        }.andReturn().response.contentAsString
        val deckId = jsonMapper.readTree(body).get("id").asLong()
        jdbc.update(
            """insert into cards (tenant_id, deck_id, front, back, position, created_at, updated_at)
               select 1, ?, 'c' || g, 'b' || g, g, now(), now() from generate_series(0, ? - 1) g""",
            deckId, cards,
        )
        if (students.isNotEmpty()) {
            mockMvc.post("/api/decks/$deckId/students") {
                auth("alpha", ana)
                json("""{"studentIds":${students.joinToString(",", "[", "]")}}""")
            }
        }
        val ids = jdbc.queryForList("select id from cards where deck_id = ? order by position", Long::class.java, deckId)
        return deckId to ids.filterNotNull()
    }

    private fun rate(cardId: Long, rating: String = "GOOD", token: String = maria) = mockMvc.post("/api/reviews") {
        auth("alpha", token)
        json("""{"cardId":$cardId,"rating":"$rating"}""")
    }

    @Test
    fun `rating a new card GOOD schedules it for the start of tomorrow`() {
        val (_, cards) = deck(1, mariaId)
        rate(cards[0]).andExpect {
            status { isOk() }
            jsonPath("$.intervalDays") { value(1) }
            jsonPath("$.repetitions") { value(1) }
            jsonPath("$.ease") { value(2.5) }
            jsonPath("$.dueAt") { value(startOfDay2.toString()) }
        }
        assert(jdbc.queryForObject("select count(*) from review_events", Long::class.java) == 1L)
    }

    @Test
    fun `cards of decks not proposed to the student are not found`() {
        val (deckId, cards) = deck(1, joaoId)
        rate(cards[0]).andExpect { status { isNotFound() } }
        mockMvc.get("/api/reviews/queue?deckId=$deckId") { auth("alpha", maria) }.andExpect { status { isNotFound() } }
        rate(cards[0], token = ana).andExpect { status { isForbidden() } }
        rate(cards[0], "MAYBE", token = joao).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `consecutive ratings build on each other`() {
        val (_, cards) = deck(1, mariaId)
        rate(cards[0])
        clock.now = day2
        rate(cards[0]).andExpect {
            jsonPath("$.intervalDays") { value(6) }
            jsonPath("$.repetitions") { value(2) }
        }
        rate(cards[0], "AGAIN").andExpect {
            jsonPath("$.intervalDays") { value(0) }
            jsonPath("$.repetitions") { value(0) }
            jsonPath("$.ease") { value(2.3) }
            jsonPath("$.dueAt") { value(day2.plus(Duration.ofMinutes(10)).toString()) }
        }
    }

    @Test
    fun `queue lists due cards first, then up to 20 new cards`() {
        val (deckId, cards) = deck(33, mariaId)
        // day 1: three cards learned, due at the start of day 2
        rate(cards[2]); rate(cards[0]); rate(cards[1])
        clock.now = day2
        // make the due order explicit: card 1 is the oldest due
        jdbc.update("update card_reviews set due_at = due_at - interval '1 hour' where card_id = ?", cards[1])

        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            status { isOk() }
            jsonPath("$.dueCount") { value(3) }
            jsonPath("$.newCount") { value(20) }
            jsonPath("$.cards", hasSize<Any>(23))
            jsonPath("$.cards[0].cardId") { value(cards[1]) }
            jsonPath("$.cards[0].isNew") { value(false) }
            jsonPath("$.cards[1].cardId") { value(cards[0]) }
            jsonPath("$.cards[2].cardId") { value(cards[2]) }
            jsonPath("$.cards[3].cardId") { value(cards[3]) }
            jsonPath("$.cards[3].isNew") { value(true) }
            jsonPath("$.cards[3].front") { value("c3") }
            jsonPath("$.cards[3].deckTitle") { value("Animals") }
            jsonPath("$.nextDueAt") { value(null as Any?) }
        }
        mockMvc.get("/api/reviews/queue?deckId=$deckId") { auth("alpha", maria) }.andExpect { jsonPath("$.cards", hasSize<Any>(23)) }
    }

    @Test
    fun `no new cards once the daily allowance is used`() {
        val (_, cards) = deck(25, mariaId)
        cards.take(20).forEach { rate(it) }
        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            jsonPath("$.dueCount") { value(0) }
            jsonPath("$.newCount") { value(0) }
            jsonPath("$.cards", hasSize<Any>(0))
            jsonPath("$.nextDueAt") { value(startOfDay2.toString()) }
        }
        // the allowance is per school day: tomorrow 5 new cards remain, after the 20 due ones
        clock.now = day2
        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            jsonPath("$.dueCount") { value(20) }
            jsonPath("$.newCount") { value(5) }
        }
    }

    @Test
    fun `allowance is per deck`() {
        val (_, animals) = deck(25, mariaId, title = "Animals")
        deck(3, mariaId, title = "Colors")
        animals.take(20).forEach { rate(it) }
        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            jsonPath("$.newCount") { value(3) }
            jsonPath("$.cards[*].deckTitle") { value(contains("Colors", "Colors", "Colors")) }
        }
    }

    @Test
    fun `again cards come back after 10 minutes`() {
        val (_, cards) = deck(1, mariaId)
        rate(cards[0], "AGAIN")
        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            jsonPath("$.cards", hasSize<Any>(0))
            jsonPath("$.nextDueAt") { value(day1.plus(Duration.ofMinutes(10)).toString()) }
        }
        clock.now = day1.plus(Duration.ofMinutes(10))
        mockMvc.get("/api/reviews/queue") { auth("alpha", maria) }.andExpect {
            jsonPath("$.dueCount") { value(1) }
            jsonPath("$.cards[0].cardId") { value(cards[0]) }
        }
    }

    @Test
    fun `teacher sees per-student progress`() {
        val (deckId, cards) = deck(30, mariaId, joaoId)
        // Maria: 3 learning cards (one due today after AGAIN), 1 mature card
        rate(cards[0]); rate(cards[1]); rate(cards[2], "AGAIN"); rate(cards[3])
        jdbc.update("update card_reviews set interval_days = 30 where card_id = ?", cards[3])
        // an old review does not count in the last 7 days
        jdbc.update("update review_events set reviewed_at = reviewed_at - interval '8 days' where card_id = ?", cards[0])

        mockMvc.get("/api/decks/$deckId/progress") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$[*].name") { value(contains("Joao", "Maria")) }
            jsonPath("$[0].newCards") { value(30) }
            jsonPath("$[0].learning") { value(0) }
            jsonPath("$[1].newCards") { value(26) }
            jsonPath("$[1].learning") { value(3) }
            jsonPath("$[1].mature") { value(1) }
            jsonPath("$[1].dueToday") { value(1) }
            jsonPath("$[1].reviewsLast7Days") { value(3) }
        }
        mockMvc.get("/api/reviews/progress") { auth("alpha", maria) }.andExpect {
            jsonPath("$[0].deckTitle") { value("Animals") }
            jsonPath("$[0].newCards") { value(26) }
            jsonPath("$[0].dueToday") { value(1) }
            jsonPath("$[0].newToday") { value(16) }
        }
    }

    @Test
    fun `progress of another teacher's deck is not found`() {
        val (deckId, _) = deck(1, mariaId)
        mockMvc.get("/api/decks/$deckId/progress") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/decks/$deckId/progress") { auth("alpha", maria) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `deleting a card removes its review state`() {
        val (_, cards) = deck(2, mariaId)
        rate(cards[0])
        mockMvc.delete("/api/cards/${cards[0]}") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        assert(jdbc.queryForObject("select count(*) from card_reviews", Long::class.java) == 0L)
    }
}
