package br.com.educare.api.lesson

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import br.com.educare.api.tenant.TenantContext
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

class LessonSeriesControllerTest : IntegrationTest() {

    @Autowired lateinit var service: LessonSeriesService

    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    var mariaId = 0L

    /** Monday, Nov 16, 2026, 09:00 in São Paulo (UTC-3, no daylight saving). */
    val monday: Instant = Instant.parse("2026-11-16T12:00:00Z")

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        maria = tokenFor("alpha", "maria@alpha.com")
        clock.now = monday
    }

    private fun createSeries(token: String = ana, weekdays: String = """"TUESDAY","THURSDAY"""", startsOn: String = "2026-11-16", time: String = "14:00") =
        mockMvc.post("/api/lesson-series") {
            auth("alpha", token)
            json("""{"studentId":$mariaId,"weekdays":[$weekdays],"startsOn":"$startsOn","startTime":"$time","durationMinutes":60,"topic":"Conversation"}""")
        }

    private fun seriesId() = jsonMapper.readTree(createSeries().andReturn().response.contentAsString).get("series").get("id").asLong()

    private fun scheduledInSeries(id: Long) =
        jdbc.queryForList("select starts_at from lessons where series_id = ? and status = 'SCHEDULED' order by starts_at", java.sql.Timestamp::class.java, id)
            .mapNotNull { it?.toInstant() }

    private fun extend() = TenantContext.runAs(1) { service.extendAll() }

    @Test
    fun `creating a series creates its lessons for the next 8 weeks`() {
        createSeries().andExpect {
            status { isCreated() }
            jsonPath("$.lessonsCreated") { value(16) }
            jsonPath("$.series.weekdays[*]") { value(contains("TUESDAY", "THURSDAY")) }
            jsonPath("$.series.startTime") { value("14:00:00") }
            jsonPath("$.series.nextLessonAt") { value("2026-11-17T17:00:00Z") }
            jsonPath("$.series.generatedUntil") { value("2027-01-11") }
        }
        mockMvc.get("/api/lessons?from=$monday&size=50") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content", hasSize<Any>(16))
            jsonPath("$.content[0].startsAt") { value("2026-11-17T17:00:00Z") }
            jsonPath("$.content[1].startsAt") { value("2026-11-19T17:00:00Z") }
            jsonPath("$.content[*].seriesId") { value(everyItem(org.hamcrest.Matchers.notNullValue())) }
            jsonPath("$.content[0].topic") { value("Conversation") }
        }
        mockMvc.get("/api/lesson-series") { auth("alpha", maria) }.andExpect { jsonPath("$", hasSize<Any>(1)) }
        mockMvc.get("/api/lesson-series") { auth("alpha", bob) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
    }

    @Test
    fun `a busy slot rejects the whole series`() {
        mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$mariaId,"startsAt":"2026-11-26T17:30:00Z","durationMinutes":60,"topic":"Extra"}""")
        }.andExpect { status { isCreated() } }
        createSeries().andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value(containsString("2026-11-26 14:00")) }
        }
        assert(jdbc.queryForObject("select count(*) from lessons", Long::class.java) == 1L)
        assert(jdbc.queryForObject("select count(*) from lesson_series", Long::class.java) == 0L)
    }

    @Test
    fun `validation and permissions`() {
        createSeries(startsOn = "2026-11-15").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.startsOn") { exists() }
        }
        createSeries(weekdays = "").andExpect { jsonPath("$.errors.weekdays") { exists() } }
        createSeries(token = maria).andExpect { status { isForbidden() } }
        createSeries(token = bob).andExpect { jsonPath("$.errors.studentId") { exists() } }
    }

    @Test
    fun `the series is extended as time passes, skipping busy slots`() {
        val id = seriesId()
        clock.now = monday.plus(Duration.ofDays(7))
        // Ana gets another lesson on the Tuesday the series would add next
        jdbc.update(
            """insert into lessons (tenant_id, teacher_id, student_id, starts_at, duration_minutes, ends_at, topic, status, created_at, updated_at)
               select 1, s.teacher_id, s.id, '2027-01-12T17:00:00Z', 60, '2027-01-12T18:00:00Z', 'Other', 'SCHEDULED', now(), now()
               from students s where s.id = ?""",
            mariaId,
        )
        assert(extend() == 1)
        val starts = scheduledInSeries(id)
        assert(starts.size == 17) { starts }
        assert(starts.last() == Instant.parse("2027-01-14T17:00:00Z"))
        assert(extend() == 0)
    }

    @Test
    fun `ending a series cancels its lessons from a date on`() {
        val id = seriesId()
        mockMvc.post("/api/lesson-series/$id/end") {
            auth("alpha", bob)
            json("""{"from":"2026-11-24"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/lesson-series/$id/end") {
            auth("alpha", ana)
            json("""{"from":"2026-11-24"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.lessonsCanceled") { value(14) }
            jsonPath("$.series.endedAt") { exists() }
        }
        assert(scheduledInSeries(id) == listOf(Instant.parse("2026-11-17T17:00:00Z"), Instant.parse("2026-11-19T17:00:00Z")))
        mockMvc.get("/api/lesson-series") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
        mockMvc.post("/api/lesson-series/$id/end") { auth("alpha", ana) }.andExpect { status { isConflict() } }
        clock.now = monday.plus(Duration.ofDays(30))
        assert(extend() == 0)
    }

    @Test
    fun `series of a deactivated student stop being extended`() {
        val id = seriesId()
        mockMvc.delete("/api/students/$mariaId") { auth("alpha", ana) }
        clock.now = monday.plus(Duration.ofDays(7))
        assert(extend() == 0)
        assert(jdbc.queryForObject("select ended_at is not null from lesson_series where id = ?", Boolean::class.java, id) == true)
    }
}
