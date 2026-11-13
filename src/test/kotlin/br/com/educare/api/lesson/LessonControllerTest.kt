package br.com.educare.api.lesson

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class LessonControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    var anaId = 0L
    var bobId = 0L
    var mariaId = 0L
    var joaoId = 0L
    var carlosId = 0L

    /** 10:00 UTC, three days from now. */
    val day: Instant = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(10))

    fun at(hours: Double): Instant = day.plusSeconds((hours * 3600).toLong() - 10 * 3600)

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        admin = tokenFor("alpha")
        anaId = createTeacher("alpha", "ana@alpha.com")
        bobId = createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        joaoId = createStudent("alpha", ana, "joao@alpha.com", name = "Joao")
        carlosId = createStudent("alpha", bob, "carlos@alpha.com", name = "Carlos")
        maria = tokenFor("alpha", "maria@alpha.com")
    }

    private fun lessonJson(studentId: Long?, startsAt: Instant, duration: Int = 60, topic: String = "Grammar") =
        """{"studentId":$studentId,"startsAt":"$startsAt","durationMinutes":$duration,"topic":"$topic"}"""

    private fun create(token: String, studentId: Long, startsAt: Instant, duration: Int = 60) =
        mockMvc.post("/api/lessons") {
            auth("alpha", token)
            json(lessonJson(studentId, startsAt, duration))
        }

    private fun createId(token: String, studentId: Long, startsAt: Instant, duration: Int = 60): Long {
        val body = create(token, studentId, startsAt, duration).andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Lesson creation failed: $body")
    }

    private fun update(token: String, id: Long, startsAt: Instant, duration: Int = 60) =
        mockMvc.put("/api/lessons/$id") {
            auth("alpha", token)
            json("""{"startsAt":"$startsAt","durationMinutes":$duration,"topic":"Updated","notes":"Bring the book"}""")
        }

    @Test
    fun `teacher schedules a lesson with one of their students`() {
        create(ana, mariaId, at(10.0), 90).andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.studentId") { value(mariaId) }
            jsonPath("$.studentName") { value("Maria") }
            jsonPath("$.teacherId") { value(anaId) }
            jsonPath("$.teacherName") { value("Teacher ana@alpha.com") }
            jsonPath("$.startsAt") { value(at(10.0).toString()) }
            jsonPath("$.endsAt") { value(at(11.5).toString()) }
            jsonPath("$.status") { value("SCHEDULED") }
        }
    }

    @Test
    fun `rejects another teacher's student`() {
        create(ana, carlosId, at(10.0)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.studentId") { exists() }
        }
    }

    @Test
    fun `rejects a past time and an invalid duration`() {
        mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json(lessonJson(mariaId, Instant.now().minus(Duration.ofHours(1)), duration = 10, topic = ""))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.startsAt") { exists() }
            jsonPath("$.errors.durationMinutes") { exists() }
            jsonPath("$.errors.topic") { exists() }
        }
        create(ana, mariaId, at(10.0), 300).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `teacher cannot be double-booked but back-to-back is allowed`() {
        createId(ana, joaoId, at(10.5))
        create(ana, mariaId, at(10.0)).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The teacher already has a lesson at this time") }
        }
        create(ana, mariaId, at(11.5)).andExpect { status { isCreated() } }
        create(ana, mariaId, at(9.5), 30).andExpect { status { isCreated() } }
    }

    @Test
    fun `student cannot be double-booked across teachers`() {
        createId(ana, mariaId, at(10.0))
        // Maria moves to Bob; Bob is free but Maria is not.
        mockMvc.put("/api/students/$mariaId") {
            auth("alpha", admin)
            json("""{"name":"Maria","email":"maria@alpha.com","teacherId":$bobId}""")
        }.andExpect { status { isOk() } }
        create(bob, mariaId, at(10.5)).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The student already has a lesson at this time") }
        }
    }

    @Test
    fun `database rejects overlaps the service did not catch`() {
        val id = createId(ana, mariaId, at(10.0))
        assertThatThrownBy {
            jdbc.update(
                """INSERT INTO lessons (tenant_id, teacher_id, student_id, starts_at, duration_minutes, ends_at, topic, status, created_at, updated_at)
                   SELECT tenant_id, teacher_id, student_id, starts_at + interval '30 minutes', 60, ends_at + interval '30 minutes', 'x', 'SCHEDULED', now(), now()
                   FROM lessons WHERE id = ?""",
                id,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java).hasMessageContaining("ex_lessons_")
    }

    @Test
    fun `teacher changes the time and overlap rules apply`() {
        val first = createId(ana, mariaId, at(10.0))
        createId(ana, joaoId, at(14.0))
        update(ana, first, at(14.5), 30).andExpect {
            status { isConflict() }
        }
        // Moving within its own slot does not conflict with itself.
        update(ana, first, at(10.5), 60).andExpect {
            status { isOk() }
            jsonPath("$.startsAt") { value(at(10.5).toString()) }
            jsonPath("$.endsAt") { value(at(11.5).toString()) }
            jsonPath("$.topic") { value("Updated") }
            jsonPath("$.notes") { value("Bring the book") }
        }
        update(ana, first, Instant.now().minusSeconds(60)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.startsAt") { exists() }
        }
    }

    @Test
    fun `canceling frees the slot and canceled lessons cannot be edited`() {
        val id = createId(ana, mariaId, at(10.0))
        mockMvc.post("/api/lessons/$id/cancel") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELED") }
        }
        create(ana, joaoId, at(10.0)).andExpect { status { isCreated() } }
        update(ana, id, at(16.0)).andExpect { status { isConflict() } }
        mockMvc.post("/api/lessons/$id/cancel") { auth("alpha", ana) }.andExpect { status { isConflict() } }
    }

    @Test
    fun `lists are scoped by role, filtered and ordered`() {
        val late = createId(ana, mariaId, at(16.0))
        val early = createId(ana, mariaId, at(9.0))
        val joao = createId(ana, joaoId, at(12.0))
        val carlos = createId(bob, carlosId, at(12.0))
        mockMvc.post("/api/lessons/$joao/cancel") { auth("alpha", ana) }

        mockMvc.get("/api/lessons") { auth("alpha", maria); param("from", Instant.now().toString()) }.andExpect {
            status { isOk() }
            jsonPath("$.content[*].id") { value(contains(early.toInt(), late.toInt())) }
        }
        mockMvc.get("/api/lessons") { auth("alpha", ana); param("status", "SCHEDULED"); param("sort", "startsAt,desc") }.andExpect {
            jsonPath("$.content[*].id") { value(contains(late.toInt(), early.toInt())) }
        }
        mockMvc.get("/api/lessons") { auth("alpha", ana); param("studentId", "$joaoId") }.andExpect {
            jsonPath("$.content[*].id") { value(contains(joao.toInt())) }
        }
        mockMvc.get("/api/lessons") { auth("alpha", ana); param("from", at(10.0).toString()); param("to", at(16.0).toString()) }.andExpect {
            jsonPath("$.content[*].id") { value(contains(joao.toInt())) }
        }
        mockMvc.get("/api/lessons") { auth("alpha", admin) }.andExpect {
            jsonPath("$.content", hasSize<Any>(4))
        }
        mockMvc.get("/api/lessons") { auth("alpha", admin); param("teacherId", "$bobId") }.andExpect {
            jsonPath("$.content[*].id") { value(contains(carlos.toInt())) }
        }
    }

    @Test
    fun `reading respects visibility and students cannot write`() {
        val id = createId(ana, mariaId, at(10.0))
        mockMvc.get("/api/lessons/$id") { auth("alpha", maria) }.andExpect { status { isOk() } }
        mockMvc.get("/api/lessons/$id") { auth("alpha", admin) }.andExpect { status { isOk() } }
        mockMvc.get("/api/lessons/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/lessons/$id") { auth("alpha", tokenFor("alpha", "joao@alpha.com")) }.andExpect { status { isNotFound() } }
        update(bob, id, at(13.0)).andExpect { status { isNotFound() } }
        update(maria, id, at(13.0)).andExpect { status { isForbidden() } }
        create(maria, mariaId, at(13.0)).andExpect { status { isForbidden() } }
        mockMvc.post("/api/lessons/$id/cancel") { auth("alpha", maria) }.andExpect { status { isForbidden() } }
        create(admin, mariaId, at(13.0)).andExpect { status { isForbidden() } }
    }

    @Test
    fun `lessons are isolated between schools`() {
        val id = createId(ana, mariaId, at(10.0))
        provisionSchool("beta")
        mockMvc.get("/api/lessons/$id") { auth("beta", tokenFor("beta")) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/lessons") { auth("beta", tokenFor("beta")) }.andExpect { jsonPath("$.content", hasSize<Any>(0)) }
    }
}
