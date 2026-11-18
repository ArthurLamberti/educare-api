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

    @Test
    fun `an online lesson keeps its room link, which must be http or https`() {
        mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$mariaId,"startsAt":"${at(10.0)}","durationMinutes":60,"topic":"Grammar","meetingUrl":"javascript:alert(1)"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.meetingUrl") { exists() }
        }
        val body = mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$mariaId,"startsAt":"${at(10.0)}","durationMinutes":60,"topic":"Grammar","meetingUrl":" https://meet.example.com/abc "}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.meetingUrl") { value("https://meet.example.com/abc") }
        }.andReturn().response.contentAsString
        val id = jsonMapper.readTree(body).get("id").asLong()
        mockMvc.get("/api/lessons/$id") { auth("alpha", maria) }.andExpect { jsonPath("$.meetingUrl") { value("https://meet.example.com/abc") } }

        // Editing without a link makes it in-person.
        mockMvc.put("/api/lessons/$id") {
            auth("alpha", ana)
            json("""{"startsAt":"${at(10.0)}","durationMinutes":60,"topic":"Grammar","meetingUrl":""}""")
        }.andExpect { jsonPath("$.meetingUrl") { value(null) } }
    }

    @Test
    fun `the teacher writes notes about a lesson, also after it, and the student reads them`() {
        val id = createId(ana, mariaId, at(10.0))
        mockMvc.put("/api/lessons/$id/annotations") {
            auth("alpha", ana)
            json("""{"text":"  Revisamos o past simple.\nPróxima aula: verbos irregulares.  "}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.annotations") { value("Revisamos o past simple.\nPróxima aula: verbos irregulares.") }
            jsonPath("$.annotationsUpdatedAt") { exists() }
        }
        mockMvc.get("/api/lessons/$id") { auth("alpha", maria) }.andExpect {
            jsonPath("$.annotations") { value("Revisamos o past simple.\nPróxima aula: verbos irregulares.") }
        }

        // After the lesson (and after it was canceled) the teacher can still write.
        clock.now = at(12.0)
        mockMvc.post("/api/lessons/$id/cancel") { auth("alpha", ana) }
        mockMvc.put("/api/lessons/$id/annotations") {
            auth("alpha", ana)
            json("""{"text":""}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.annotations") { value(null) }
        }

        // Only the lesson's teacher.
        listOf(bob, maria, admin).forEach { token ->
            mockMvc.put("/api/lessons/$id/annotations") {
                auth("alpha", token)
                json("""{"text":"x"}""")
            }.andExpect { status { is4xxClientError() } }
        }
        mockMvc.put("/api/lessons/$id/annotations") {
            auth("alpha", ana)
            json("""{"text":"${"a".repeat(10001)}"}""")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a weekly series copies its room link to every lesson`() {
        val startsOn = day.atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate()
        mockMvc.post("/api/lesson-series") {
            auth("alpha", ana)
            json(
                """{"studentId":$mariaId,"weekdays":["${startsOn.dayOfWeek}"],"startsOn":"$startsOn","startTime":"15:00",
                   "durationMinutes":60,"topic":"Conversation","meetingUrl":"https://zoom.example.com/j/1"}""",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.series.meetingUrl") { value("https://zoom.example.com/j/1") }
        }
        val lessonsUrls = jdbc.queryForList("SELECT DISTINCT meeting_url FROM lessons", String::class.java)
        kotlin.test.assertEquals(listOf("https://zoom.example.com/j/1"), lessonsUrls)
    }

    private fun room(id: Long, token: String) =
        jsonMapper.readTree(mockMvc.get("/api/lessons/$id/room") { auth("alpha", token) }.andReturn().response.contentAsString)

    private fun roomUrl(id: Long, token: String): String = room(id, token).get("url").asString()

    @Test
    fun `a virtual room is joined through the lesson, never through a stored provider link`() {
        val body = mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$mariaId,"startsAt":"${at(10.0)}","durationMinutes":60,"topic":"Grammar","virtualRoom":true}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.virtualRoom") { value(true) }
            jsonPath("$.meetingUrl") { value(null) }
        }.andReturn().response.contentAsString
        val id = jsonMapper.readTree(body).get("id").asLong()

        // Teacher and student land in the same room, each with their own name; only the teacher hosts;
        // access lasts until an hour after the lesson. The key is unguessable.
        val teacherRoom = room(id, ana)
        kotlin.test.assertTrue(teacherRoom.get("embedded").asBoolean())
        kotlin.test.assertEquals("fake", teacherRoom.get("provider").asString())
        kotlin.test.assertTrue(teacherRoom.get("token").asString().matches(Regex("token-user-\\d+")), teacherRoom.toString())
        val teacherUrl = roomUrl(id, ana)
        val studentUrl = roomUrl(id, maria)
        val until = at(11.0).plus(Duration.ofHours(1)).epochSecond
        kotlin.test.assertTrue(teacherUrl.matches(Regex("""https://rooms\.test/[0-9a-f]{32}\?name=Teacher.*&host=true&until=$until""")), teacherUrl)
        kotlin.test.assertEquals(teacherUrl.substringBefore('?'), studentUrl.substringBefore('?'))
        kotlin.test.assertTrue(studentUrl.contains("name=Maria&host=false"), studentUrl)

        // Editing keeps the room; other users cannot get it.
        mockMvc.put("/api/lessons/$id") {
            auth("alpha", ana)
            json("""{"startsAt":"${at(11.0)}","durationMinutes":60,"topic":"Grammar","virtualRoom":true}""")
        }.andExpect { jsonPath("$.virtualRoom") { value(true) } }
        kotlin.test.assertEquals(teacherUrl.substringBefore('?'), roomUrl(id, ana).substringBefore('?'))
        listOf(bob, admin).forEach { token ->
            mockMvc.get("/api/lessons/$id/room") { auth("alpha", token) }.andExpect { status { isNotFound() } }
        }

        // A room and a pasted link at once is refused.
        mockMvc.put("/api/lessons/$id") {
            auth("alpha", ana)
            json("""{"startsAt":"${at(11.0)}","durationMinutes":60,"topic":"Grammar","virtualRoom":true,"meetingUrl":"https://zoom.example.com/j/1"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.meetingUrl") { exists() }
        }

        // Switching to the teacher's own link drops the room; the room endpoint then returns that link.
        mockMvc.put("/api/lessons/$id") {
            auth("alpha", ana)
            json("""{"startsAt":"${at(11.0)}","durationMinutes":60,"topic":"Grammar","meetingUrl":"https://zoom.example.com/j/1"}""")
        }.andExpect { jsonPath("$.virtualRoom") { value(false) } }
        kotlin.test.assertEquals("https://zoom.example.com/j/1", roomUrl(id, maria))
        val ownLink = room(id, maria)
        kotlin.test.assertFalse(ownLink.get("embedded").asBoolean())
        kotlin.test.assertTrue(ownLink.get("token").isNull, ownLink.toString())

        // In-person lessons have no room, and canceled lessons cannot be joined.
        val inPerson = createId(ana, joaoId, at(14.0))
        mockMvc.get("/api/lessons/$inPerson/room") { auth("alpha", ana) }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/lessons/$id/cancel") { auth("alpha", ana) }
        mockMvc.get("/api/lessons/$id/room") { auth("alpha", maria) }.andExpect { status { isConflict() } }
    }

    @Test
    fun `a weekly series with a virtual room gives each lesson its own room`() {
        val startsOn = day.atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate()
        mockMvc.post("/api/lesson-series") {
            auth("alpha", ana)
            json(
                """{"studentId":$mariaId,"weekdays":["${startsOn.dayOfWeek}"],"startsOn":"$startsOn","startTime":"15:00",
                   "durationMinutes":60,"topic":"Conversation","virtualRoom":true}""",
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.series.virtualRoom") { value(true) }
        }
        val keys = jdbc.queryForList("SELECT virtual_room_key FROM lessons", String::class.java)
        kotlin.test.assertTrue(keys.size > 1 && keys.all { it != null } && keys.toSet().size == keys.size, keys.toString())
    }
}
