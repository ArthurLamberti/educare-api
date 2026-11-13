package br.com.educare.api.assignment

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
import java.time.temporal.ChronoUnit

class AssignmentControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var mariaId = 0L
    var joaoId = 0L
    var bobStudentId = 0L

    val dueAt: Instant = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.SECONDS)

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
        bobStudentId = createStudent("alpha", bob, "carla@alpha.com", name = "Carla")
        maria = tokenFor("alpha", "maria@alpha.com")
        joao = tokenFor("alpha", "joao@alpha.com")
    }

    private fun task(token: String = ana, title: String = "Irregular verbs"): Long {
        val body = mockMvc.post("/api/tasks") {
            auth("alpha", token)
            json("""{"title":"$title","instructions":"Write 10 sentences."}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Task creation failed: $body")
    }

    private fun assign(taskId: Long, vararg studentIds: Long, due: Instant? = dueAt, token: String = ana) =
        mockMvc.post("/api/tasks/$taskId/assignments") {
            auth("alpha", token)
            json("""{"studentIds":${studentIds.joinToString(",", "[", "]")}${due?.let { ""","dueAt":"$it"""" } ?: ""}}""")
        }

    private fun assignedId(taskId: Long, studentId: Long, due: Instant? = dueAt): Long {
        val body = assign(taskId, studentId, due = due).andReturn().response.contentAsString
        return jsonMapper.readTree(body).get(0)?.get("id")?.asLong() ?: error("Assign failed: $body")
    }

    private fun count(table: String) = jdbc.queryForObject("select count(*) from $table", Long::class.java)

    @Test
    fun `teacher assigns a task to two students`() {
        val taskId = task()
        assign(taskId, mariaId, joaoId).andExpect {
            status { isCreated() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[*].status") { value(contains("ASSIGNED", "ASSIGNED")) }
            jsonPath("$[*].studentName") { value(contains("Maria", "Joao")) }
            jsonPath("$[0].dueAt") { value(dueAt.toString()) }
            jsonPath("$[0].taskTitle") { value("Irregular verbs") }
            jsonPath("$[0].overdue") { value(false) }
        }
        assign(task(title = "No due date"), mariaId, due = null).andExpect {
            status { isCreated() }
            jsonPath("$[0].dueAt") { value(null as Any?) }
        }
    }

    @Test
    fun `student of another teacher is rejected and nothing is created`() {
        val taskId = task()
        assign(taskId, mariaId, bobStudentId).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.studentIds") { exists() }
        }
        assign(taskId, mariaId, 9999).andExpect { status { isBadRequest() } }
        assert(count("assignments") == 0L)
    }

    @Test
    fun `invalid assign requests`() {
        val taskId = task()
        assign(taskId).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.studentIds") { exists() }
        }
        assign(taskId, mariaId, due = Instant.now().minusSeconds(60)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.dueAt") { exists() }
        }
        assign(task(bob), mariaId).andExpect { status { isNotFound() } }
    }

    @Test
    fun `already assigned students make the whole request fail`() {
        val taskId = task()
        assign(taskId, mariaId).andExpect { status { isCreated() } }
        assign(taskId, joaoId, mariaId).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This task is already assigned to: Maria") }
        }
        assert(count("assignments") == 1L)
    }

    @Test
    fun `archived task cannot be assigned`() {
        val taskId = task()
        mockMvc.post("/api/tasks/$taskId/archive") { auth("alpha", ana) }
        assign(taskId, mariaId).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Archived tasks cannot be assigned") }
        }
    }

    @Test
    fun `teacher unassigns untouched work`() {
        val taskId = task()
        val id = assignedId(taskId, mariaId)
        mockMvc.delete("/api/assignments/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/assignments/$id") { auth("alpha", maria) }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/assignments/$id") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/assignments") { auth("alpha", maria) }.andExpect { jsonPath("$.totalElements") { value(0) } }
        // the task is unused again, so it can be deleted
        mockMvc.delete("/api/tasks/$taskId") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
    }

    @Test
    fun `submitted work cannot be unassigned`() {
        val id = assignedId(task(), mariaId)
        jdbc.update("update assignments set status = 'SUBMITTED' where id = ?", id)
        mockMvc.delete("/api/assignments/$id") { auth("alpha", ana) }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Assignments with a submission cannot be removed") }
        }
    }

    @Test
    fun `student to-do list shows only their pending work, soonest due first`() {
        val later = assignedId(task(title = "Later"), mariaId, dueAt.plus(Duration.ofDays(3)))
        val noDue = assignedId(task(title = "No due"), mariaId, null)
        val soon = assignedId(task(title = "Soon"), mariaId, dueAt)
        val done = assignedId(task(title = "Done"), mariaId, dueAt)
        jdbc.update("update assignments set status = 'GRADED' where id = ?", done)
        assignedId(task(title = "Joao's"), joaoId)

        mockMvc.get("/api/assignments?status=ASSIGNED") { auth("alpha", maria) }.andExpect {
            status { isOk() }
            jsonPath("$.content[*].id") { value(contains(soon.toInt(), later.toInt(), noDue.toInt())) }
            jsonPath("$.content[0].instructions") { value("Write 10 sentences.") }
        }
        mockMvc.get("/api/assignments?status=SUBMITTED&status=GRADED") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[*].id") { value(contains(done.toInt())) }
        }
        mockMvc.get("/api/assignments/$soon") { auth("alpha", maria) }.andExpect {
            status { isOk() }
            jsonPath("$.taskTitle") { value("Soon") }
        }
        mockMvc.get("/api/assignments/$soon") { auth("alpha", joao) }.andExpect { status { isNotFound() } }
        // a student cannot read other students' work through the studentId filter
        mockMvc.get("/api/assignments?studentId=$joaoId") { auth("alpha", maria) }.andExpect {
            jsonPath("$.totalElements") { value(4) }
        }
    }

    @Test
    fun `overdue is computed with the clock`() {
        val id = assignedId(task(), mariaId, dueAt)
        val graded = assignedId(task(title = "Graded"), mariaId, dueAt)
        jdbc.update("update assignments set status = 'GRADED' where id = ?", graded)

        clock.now = dueAt.minusSeconds(1)
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect { jsonPath("$.overdue") { value(false) } }

        clock.now = dueAt.plusSeconds(1)
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect { jsonPath("$.overdue") { value(true) } }
        mockMvc.get("/api/assignments/$graded") { auth("alpha", maria) }.andExpect { jsonPath("$.overdue") { value(false) } }
    }

    @Test
    fun `teachers see assignments of their tasks, admins see all`() {
        val anaTask = task()
        assign(anaTask, mariaId, joaoId)
        assign(task(bob, "Bob's task"), bobStudentId, token = bob)

        mockMvc.get("/api/assignments") { auth("alpha", ana) }.andExpect { jsonPath("$.totalElements") { value(2) } }
        mockMvc.get("/api/assignments?studentId=$joaoId") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].studentName") { value(contains("Joao")) }
        }
        mockMvc.get("/api/assignments?taskId=$anaTask") { auth("alpha", bob) }.andExpect {
            jsonPath("$.totalElements") { value(0) }
        }
        mockMvc.get("/api/assignments") { auth("alpha", bob) }.andExpect {
            jsonPath("$.content[*].taskTitle") { value(contains("Bob's task")) }
        }
        mockMvc.get("/api/assignments") { auth("alpha", admin) }.andExpect { jsonPath("$.totalElements") { value(3) } }
    }

    @Test
    fun `students cannot write`() {
        val taskId = task()
        mockMvc.post("/api/tasks") {
            auth("alpha", maria)
            json("""{"title":"x","instructions":"y"}""")
        }.andExpect { status { isForbidden() } }
        assign(taskId, mariaId, token = maria).andExpect { status { isForbidden() } }
    }
}
