package br.com.educare.api.submission

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class SubmissionControllerTest : IntegrationTest() {

    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var mariaId = 0L
    var joaoId = 0L
    var taskId = 0L

    val dueAt: Instant = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.SECONDS)

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
        taskId = task("Irregular verbs", "Write 10 sentences.")
    }

    private fun task(title: String, instructions: String): Long {
        val body = mockMvc.post("/api/tasks") {
            auth("alpha", ana)
            json("""{"title":"$title","instructions":"$instructions"}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id").asLong()
    }

    private fun assign(studentId: Long, task: Long = taskId): Long {
        val body = mockMvc.post("/api/tasks/$task/assignments") {
            auth("alpha", ana)
            json("""{"studentIds":[$studentId],"dueAt":"$dueAt"}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get(0).get("id").asLong()
    }

    private fun submit(token: String, id: Long, answer: String = "My answer") = mockMvc.put("/api/assignments/$id/submission") {
        auth("alpha", token)
        json("""{"answer":"$answer"}""")
    }

    private fun grade(id: Long, score: String, feedback: String = "Good work", version: Long? = null, token: String = ana) =
        mockMvc.put("/api/assignments/$id/grade") {
            auth("alpha", token)
            json("""{"score":$score,"feedback":"$feedback"${version?.let { ""","version":$it""" } ?: ""}}""")
        }

    @Test
    fun `first submission moves the assignment to SUBMITTED and snapshots the instructions`() {
        val id = assign(mariaId)
        clock.now = dueAt.minus(Duration.ofDays(1))
        submit(maria, id, "  I went to school.  ").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.overdue") { value(false) }
            jsonPath("$.submission.answer") { value("I went to school.") }
            jsonPath("$.submission.submittedAt") { value(clock.now.toString()) }
            jsonPath("$.submission.late") { value(false) }
            jsonPath("$.submission.instructionsSnapshot") { value("Write 10 sentences.") }
            jsonPath("$.grade") { value(null as Any?) }
        }
        // later task edits do not change what was answered
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Irregular verbs","instructions":"New instructions"}""")
        }
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect {
            jsonPath("$.instructions") { value("New instructions") }
            jsonPath("$.submission.instructionsSnapshot") { value("Write 10 sentences.") }
        }
    }

    @Test
    fun `student edits the answer before grading, keeping the first submission time`() {
        val id = assign(mariaId)
        val first = dueAt.minus(Duration.ofDays(2))
        clock.now = first
        submit(maria, id, "First")
        clock.now = first.plus(Duration.ofHours(1))
        submit(maria, id, "Second").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.submission.answer") { value("Second") }
            jsonPath("$.submission.submittedAt") { value(first.toString()) }
            jsonPath("$.submission.updatedAt") { value(clock.now.toString()) }
            jsonPath("$.submission.version") { value(1) }
        }
    }

    @Test
    fun `graded work cannot be changed`() {
        val id = assign(mariaId)
        submit(maria, id)
        grade(id, "8").andExpect { status { isOk() } }
        submit(maria, id, "Too late").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("Graded work can no longer be changed") }
        }
    }

    @Test
    fun `someone else's assignment is not found`() {
        val id = assign(mariaId)
        submit(joao, id).andExpect { status { isNotFound() } }
        submit(ana, id).andExpect { status { isForbidden() } }
        submit(maria, id).andExpect { status { isOk() } }
        grade(id, "5", token = bob).andExpect { status { isNotFound() } }
        grade(id, "5", token = maria).andExpect { status { isForbidden() } }
    }

    @Test
    fun `invalid answers are rejected`() {
        val id = assign(mariaId)
        submit(maria, id, " ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.answer") { exists() }
        }
    }

    @Test
    fun `late submission is accepted and flagged`() {
        val id = assign(mariaId)
        clock.now = dueAt.plusSeconds(1)
        submit(maria, id).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.overdue") { value(false) }
            jsonPath("$.submission.late") { value(true) }
        }
    }

    @Test
    fun `teacher grades a submission`() {
        val id = assign(mariaId)
        submit(maria, id)
        grade(id, "8.5", "Nice, check **verb tenses**.").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.grade.score") { value(8.5) }
            jsonPath("$.grade.feedback") { value("Nice, check **verb tenses**.") }
            jsonPath("$.grade.gradedAt") { exists() }
            jsonPath("$.grade.gradedByName") { value("Teacher ana@alpha.com") }
        }
    }

    @Test
    fun `grading before submission is refused`() {
        val id = assign(mariaId)
        grade(id, "7").andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The student has not submitted an answer yet") }
        }
    }

    @Test
    fun `invalid scores are rejected`() {
        val id = assign(mariaId)
        submit(maria, id)
        for (score in listOf("10.5", "-1", "7.25", "null")) {
            grade(id, score).andExpect {
                status { isBadRequest() }
                jsonPath("$.errors.score") { exists() }
            }
        }
        grade(id, "10").andExpect { status { isOk() } }
        grade(id, "0").andExpect { status { isOk() } }
        grade(id, "7", feedback = " ").andExpect { jsonPath("$.errors.feedback") { exists() } }
    }

    @Test
    fun `teacher revises a grade`() {
        val id = assign(mariaId)
        submit(maria, id)
        grade(id, "6", "Needs work")
        grade(id, "7.5", "Better after review").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.grade.score") { value(7.5) }
            jsonPath("$.grade.feedback") { value("Better after review") }
        }
    }

    @Test
    fun `a grade for an answer version the teacher did not see is refused`() {
        val id = assign(mariaId)
        submit(maria, id, "First") // version 0
        submit(maria, id, "Second") // version 1
        grade(id, "9", version = 0).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("The student changed the answer after you opened it; reload to review the new answer") }
        }
        mockMvc.get("/api/assignments/$id") { auth("alpha", ana) }.andExpect { jsonPath("$.status") { value("SUBMITTED") } }
        grade(id, "9", version = 1).andExpect { status { isOk() } }
    }

    @Test
    fun `grading queue lists the oldest submission first`() {
        val mariaWork = assign(mariaId)
        val joaoWork = assign(joaoId)
        val pending = assign(mariaId, task("Other", "x"))
        clock.now = dueAt.minus(Duration.ofDays(3))
        submit(joao, joaoWork)
        clock.now = dueAt.minus(Duration.ofDays(2))
        submit(maria, mariaWork)
        clock.now = dueAt.minus(Duration.ofDays(1))
        submit(joao, joaoWork, "edited later") // editing does not move it back in the queue

        mockMvc.get("/api/assignments?status=SUBMITTED") { auth("alpha", ana) }.andExpect {
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.content[*].id") { value(contains(joaoWork.toInt(), mariaWork.toInt())) }
        }
        mockMvc.get("/api/assignments?status=ASSIGNED") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].id") { value(contains(pending.toInt())) }
        }
    }

    @Test
    fun `student sees the grade only once graded`() {
        val id = assign(mariaId)
        submit(maria, id, "My answer")
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect {
            jsonPath("$.submission.answer") { value("My answer") }
            jsonPath("$.grade") { value(null as Any?) }
        }
        grade(id, "9.5", "Excellent")
        mockMvc.get("/api/assignments?status=GRADED") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content[0].submission.answer") { value("My answer") }
            jsonPath("$.content[0].grade.score") { value(9.5) }
            jsonPath("$.content[0].grade.feedback") { value("Excellent") }
        }
    }
}
