package br.com.educare.api.reschedule

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class RescheduleRequestControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var mariaId = 0L
    var joaoId = 0L

    /** The lesson start used by most tests: 10 days from now, 10:00 UTC. */
    val lessonAt: Instant = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(10))
    val proposed: Instant = lessonAt.plus(Duration.ofDays(1))

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
        maria = tokenFor("alpha", "maria@alpha.com")
        joao = tokenFor("alpha", "joao@alpha.com")
    }

    private fun lesson(studentId: Long, startsAt: Instant = lessonAt, topic: String = "Grammar"): Long {
        val body = mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$studentId,"startsAt":"$startsAt","durationMinutes":60,"topic":"$topic"}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Lesson creation failed: $body")
    }

    private fun request(token: String, lessonId: Long, at: Instant = proposed, reason: String = "Doctor appointment") =
        mockMvc.post("/api/lessons/$lessonId/reschedule-requests") {
            auth("alpha", token)
            json("""{"proposedStartsAt":"$at","reason":"$reason"}""")
        }

    private fun requestId(token: String, lessonId: Long, at: Instant = proposed): Long {
        val body = request(token, lessonId, at).andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Request failed: $body")
    }

    private fun decide(token: String, id: Long, action: String, body: String? = null) =
        mockMvc.post("/api/reschedule-requests/$id/$action") {
            auth("alpha", token)
            body?.let { json(it) }
        }

    private fun lessonStartsAt(lessonId: Long): String =
        jsonMapper.readTree(mockMvc.get("/api/lessons/$lessonId") { auth("alpha", ana) }.andReturn().response.contentAsString)
            .get("startsAt").asString()

    @Test
    fun `student requests a new time well in advance`() {
        val lessonId = lesson(mariaId)
        clock.now = lessonAt.minus(Duration.ofDays(5))
        request(maria, lessonId).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.lessonId") { value(lessonId) }
            jsonPath("$.lessonTopic") { value("Grammar") }
            jsonPath("$.originalStartsAt") { value(lessonAt.toString()) }
            jsonPath("$.lessonStartsAt") { value(lessonAt.toString()) }
            jsonPath("$.proposedStartsAt") { value(proposed.toString()) }
            jsonPath("$.reason") { value("Doctor appointment") }
            jsonPath("$.studentName") { value("Maria") }
        }
    }

    @Test
    fun `the request must be made at least 48 hours before the lesson`() {
        val lessonId = lesson(mariaId)
        clock.now = lessonAt.minus(Duration.ofHours(47))
        request(maria, lessonId).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.detail") { value("Changes must be requested at least 2 days (48 hours) before the lesson starts") }
        }
        mockMvc.get("/api/reschedule-requests") { auth("alpha", maria) }.andExpect { jsonPath("$.content", hasSize<Any>(0)) }

        clock.now = lessonAt.minus(Duration.ofHours(48))
        request(maria, lessonId).andExpect { status { isCreated() } }
    }

    @Test
    fun `only the lesson's student can ask`() {
        val lessonId = lesson(mariaId)
        request(joao, lessonId).andExpect { status { isNotFound() } }
        request(ana, lessonId).andExpect { status { isForbidden() } }
        request(admin, lessonId).andExpect { status { isForbidden() } }
    }

    @Test
    fun `proposed time must be future and free`() {
        val lessonId = lesson(mariaId)
        lesson(joaoId, proposed)
        request(maria, lessonId, Instant.now().minusSeconds(60)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.proposedStartsAt") { exists() }
        }
        request(maria, lessonId, proposed.plus(Duration.ofMinutes(30))).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The teacher already has a lesson at this time") }
        }
    }

    @Test
    fun `one pending request per lesson and the student can withdraw it`() {
        val lessonId = lesson(mariaId)
        val first = requestId(maria, lessonId)
        request(maria, lessonId, proposed.plus(Duration.ofHours(2))).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This lesson already has a pending change request") }
        }
        decide(joao, first, "withdraw").andExpect { status { isNotFound() } }
        decide(maria, first, "withdraw").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("WITHDRAWN") }
        }
        decide(maria, first, "withdraw").andExpect { status { isConflict() } }
        request(maria, lessonId, proposed.plus(Duration.ofHours(2))).andExpect { status { isCreated() } }
    }

    @Test
    fun `teacher approves and the lesson moves`() {
        val lessonId = lesson(mariaId)
        val id = requestId(maria, lessonId)
        decide(bob, id, "approve").andExpect { status { isNotFound() } }
        decide(maria, id, "approve").andExpect { status { isForbidden() } }
        decide(ana, id, "approve").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.decidedByName") { value("Teacher ana@alpha.com") }
            jsonPath("$.lessonStartsAt") { value(proposed.toString()) }
            jsonPath("$.originalStartsAt") { value(lessonAt.toString()) }
        }
        check(lessonStartsAt(lessonId) == proposed.toString())
        decide(ana, id, "approve").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This request was already approved") }
        }
        decide(ana, id, "reject").andExpect { status { isConflict() } }
    }

    @Test
    fun `approving into a conflict keeps the request pending and the lesson unchanged`() {
        val lessonId = lesson(mariaId)
        val id = requestId(maria, lessonId)
        lesson(joaoId, proposed)
        decide(ana, id, "approve").andExpect { status { isConflict() } }
        check(lessonStartsAt(lessonId) == lessonAt.toString())
        mockMvc.get("/api/reschedule-requests") { auth("alpha", ana); param("status", "PENDING") }.andExpect {
            jsonPath("$.content[*].id") { value(contains(id.toInt())) }
        }
    }

    @Test
    fun `approving after the proposed time has passed is a conflict`() {
        val lessonId = lesson(mariaId)
        val id = requestId(maria, lessonId)
        clock.now = proposed.plusSeconds(1)
        decide(ana, id, "approve").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The proposed time has already passed") }
        }
    }

    @Test
    fun `teacher rejects with a note`() {
        val lessonId = lesson(mariaId)
        val id = requestId(maria, lessonId)
        decide(ana, id, "reject", """{"note":"I am not available then"}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.decisionNote") { value("I am not available then") }
        }
        check(lessonStartsAt(lessonId) == lessonAt.toString())
        mockMvc.get("/api/reschedule-requests") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[0].status") { value("REJECTED") }
            jsonPath("$.content[0].decisionNote") { value("I am not available then") }
        }
    }

    @Test
    fun `a direct time change or a cancel closes the pending request`() {
        val lessonId = lesson(mariaId)
        val id = requestId(maria, lessonId)
        // Changing only the topic keeps it pending.
        mockMvc.put("/api/lessons/$lessonId") {
            auth("alpha", ana)
            json("""{"startsAt":"$lessonAt","durationMinutes":60,"topic":"Renamed"}""")
        }.andExpect { status { isOk() } }
        statusOf(maria, id, "PENDING")
        mockMvc.put("/api/lessons/$lessonId") {
            auth("alpha", ana)
            json("""{"startsAt":"${lessonAt.plus(Duration.ofHours(3))}","durationMinutes":60,"topic":"Renamed"}""")
        }.andExpect { status { isOk() } }
        statusOf(maria, id, "CANCELED")

        val other = lesson(joaoId, lessonAt.plus(Duration.ofDays(2)))
        val second = requestId(joao, other, lessonAt.plus(Duration.ofDays(3)))
        mockMvc.post("/api/lessons/$other/cancel") { auth("alpha", ana) }.andExpect { status { isOk() } }
        statusOf(joao, second, "CANCELED")
        request(joao, other, lessonAt.plus(Duration.ofDays(4))).andExpect { status { isConflict() } }
    }

    private fun statusOf(token: String, id: Long, expected: String) {
        mockMvc.get("/api/reschedule-requests") { auth("alpha", token) }.andExpect {
            jsonPath("$.content[?(@.id == $id)].status") { value(contains(expected)) }
        }
    }

    @Test
    fun `lists are scoped by role, newest first and filterable`() {
        val mariaLesson = lesson(mariaId)
        val joaoLesson = lesson(joaoId, lessonAt.plus(Duration.ofHours(2)), "Reading")
        clock.now = Instant.now()
        val m1 = requestId(maria, mariaLesson)
        decide(maria, m1, "withdraw")
        clock.now = Instant.now().plusSeconds(10)
        val m2 = requestId(maria, mariaLesson)
        clock.now = Instant.now().plusSeconds(20)
        val j1 = requestId(joao, joaoLesson, lessonAt.plus(Duration.ofDays(2)))

        mockMvc.get("/api/reschedule-requests") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[*].id") { value(contains(m2.toInt(), m1.toInt())) }
        }
        mockMvc.get("/api/reschedule-requests") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].id") { value(contains(j1.toInt(), m2.toInt(), m1.toInt())) }
        }
        mockMvc.get("/api/reschedule-requests") { auth("alpha", ana); param("status", "PENDING") }.andExpect {
            jsonPath("$.content[*].id") { value(contains(j1.toInt(), m2.toInt())) }
            jsonPath("$.totalElements") { value(2) }
        }
        mockMvc.get("/api/reschedule-requests") { auth("alpha", bob) }.andExpect { jsonPath("$.content", hasSize<Any>(0)) }
        mockMvc.get("/api/reschedule-requests") { auth("alpha", admin) }.andExpect { jsonPath("$.content", hasSize<Any>(3)) }
    }
}
