package br.com.educare.api.submission

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Change 15: private drafts of the student's answer and of the teacher's correction. */
class DraftControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    lateinit var joao: String
    var id = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        admin = tokenFor("alpha", "admin@alpha.com")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        val mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        createStudent("alpha", ana, "joao@alpha.com", name = "Joao")
        maria = tokenFor("alpha", "maria@alpha.com")
        joao = tokenFor("alpha", "joao@alpha.com")
        val task = mockMvc.post("/api/tasks") {
            auth("alpha", ana)
            json("""{"title":"Essay","instructions":"Write about your weekend."}""")
        }.andReturn().response.contentAsString
        val assigned = mockMvc.post("/api/tasks/${jsonMapper.readTree(task).get("id").asLong()}/assignments") {
            auth("alpha", ana)
            json("""{"studentIds":[$mariaId]}""")
        }.andReturn().response.contentAsString
        id = jsonMapper.readTree(assigned).get(0).get("id").asLong()
    }

    private fun draft(answer: String, token: String = maria) = mockMvc.put("/api/assignments/$id/draft") {
        auth("alpha", token)
        json("""{"answer":"$answer"}""")
    }

    private fun submit(answer: String) = mockMvc.put("/api/assignments/$id/submission") {
        auth("alpha", maria)
        json("""{"answer":"$answer"}""")
    }

    private fun gradeDraft(body: String, token: String = ana) = mockMvc.put("/api/assignments/$id/grade-draft") {
        auth("alpha", token)
        json(body)
    }

    private fun grade(score: String, feedback: String) = mockMvc.put("/api/assignments/$id/grade") {
        auth("alpha", ana)
        json("""{"score":$score,"feedback":"$feedback"}""")
    }

    private fun read(token: String) = mockMvc.get("/api/assignments/$id") { auth("alpha", token) }

    private fun version(): Long = jsonMapper.readTree(read(ana).andReturn().response.contentAsString).get("submission").get("version").asLong()

    @Test
    fun `a draft before the first submission stays private and keeps the status`() {
        draft("first ideas").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ASSIGNED") }
            jsonPath("$.submission") { value(nullValue()) }
            jsonPath("$.draft.answer") { value("first ideas") }
            jsonPath("$.draft.savedAt") { value(notNullValue()) }
        }
        read(maria).andExpect { jsonPath("$.draft.answer") { value("first ideas") } }
        listOf(ana, admin).forEach { token ->
            read(token).andExpect {
                jsonPath("$.status") { value("ASSIGNED") }
                jsonPath("$.submission") { value(nullValue()) }
                jsonPath("$.draft") { value(nullValue()) }
            }
        }
        mockMvc.get("/api/assignments?status=SUBMITTED") { auth("alpha", ana) }.andExpect { jsonPath("$.totalElements") { value(0) } }
    }

    @Test
    fun `a blank draft is allowed and submitting clears the draft`() {
        draft("").andExpect {
            status { isOk() }
            jsonPath("$.draft.answer") { value("") }
        }
        draft("almost done")
        submit("final answer").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.submission.answer") { value("final answer") }
            jsonPath("$.draft") { value(nullValue()) }
        }
    }

    @Test
    fun `after submitting, the teacher keeps seeing the sent answer while the student drafts and resubmits`() {
        submit("version one")
        draft("version two, in progress").andExpect {
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.submission.answer") { value("version one") }
            jsonPath("$.draft.answer") { value("version two, in progress") }
        }
        read(ana).andExpect {
            jsonPath("$.submission.answer") { value("version one") }
            jsonPath("$.draft") { value(nullValue()) }
        }
        // Resubmitting until graded keeps working (item 5).
        submit("version two").andExpect { jsonPath("$.submission.answer") { value("version two") } }
        read(ana).andExpect { jsonPath("$.submission.answer") { value("version two") } }
    }

    @Test
    fun `drafts are refused for graded work and for other students`() {
        draft("x", token = joao).andExpect { status { isNotFound() } }
        draft("x", token = ana).andExpect { status { isForbidden() } }
        submit("done")
        grade("9", "Great")
        draft("change").andExpect { status { isConflict() } }
    }

    @Test
    fun `the teacher's correction draft is private until sent`() {
        gradeDraft("""{"score":7}""").andExpect { status { isConflict() } } // nothing submitted yet
        submit("my essay")
        gradeDraft("""{"score":7.5,"feedback":"  Check the verbs.  ","version":${version()}}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.grade") { value(nullValue()) }
            jsonPath("$.gradeDraft.score") { value(7.5) }
            jsonPath("$.gradeDraft.feedback") { value("Check the verbs.") }
            jsonPath("$.hasGradeDraft") { value(true) }
        }
        read(maria).andExpect {
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.grade") { value(nullValue()) }
            jsonPath("$.gradeDraft") { value(nullValue()) }
            jsonPath("$.hasGradeDraft") { value(false) }
        }
        read(admin).andExpect { jsonPath("$.gradeDraft") { value(nullValue()) } }
        mockMvc.get("/api/assignments?status=SUBMITTED") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[0].hasGradeDraft") { value(true) }
        }
        // Only score, only feedback, or nothing are all valid drafts.
        gradeDraft("""{"feedback":"Still reading"}""").andExpect {
            jsonPath("$.gradeDraft.score") { value(nullValue()) }
            jsonPath("$.gradeDraft.feedback") { value("Still reading") }
        }
        grade("8", "Good").andExpect {
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.gradeDraft") { value(nullValue()) }
            jsonPath("$.hasGradeDraft") { value(false) }
        }
        read(maria).andExpect { jsonPath("$.grade.score") { value(8.0) } }
    }

    @Test
    fun `a revision draft keeps the published grade visible to the student`() {
        submit("my essay")
        grade("6", "Needs work")
        gradeDraft("""{"score":8,"feedback":"Re-read: better than I thought"}""").andExpect {
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.grade.score") { value(6.0) }
            jsonPath("$.gradeDraft.score") { value(8.0) }
        }
        read(maria).andExpect {
            jsonPath("$.grade.score") { value(6.0) }
            jsonPath("$.grade.feedback") { value("Needs work") }
        }
        grade("8", "Re-read: better than I thought")
        read(maria).andExpect { jsonPath("$.grade.score") { value(8.0) } }
    }

    @Test
    fun `a correction draft for an answer that changed meanwhile is refused`() {
        submit("version one")
        val seen = version()
        submit("version two")
        gradeDraft("""{"score":5,"version":$seen}""").andExpect { status { isConflict() } }
        gradeDraft("""{"score":5,"version":${version()}}""").andExpect { status { isOk() } }
        // Saving a correction draft does not change the answer version.
        val before = version()
        gradeDraft("""{"score":6,"version":$before}""").andExpect { status { isOk() } }
        grade("6", "ok").andExpect { status { isOk() } }
    }

    @Test
    fun `the student can resubmit while the teacher has an unsent correction`() {
        submit("version one")
        gradeDraft("""{"score":5}""")
        submit("version two").andExpect {
            status { isOk() }
            jsonPath("$.submission.answer") { value("version two") }
        }
        read(ana).andExpect { jsonPath("$.gradeDraft.score") { value(5.0) } }
    }

    @Test
    fun `correction drafts follow the grading rules`() {
        submit("my essay")
        gradeDraft("""{"score":10.5}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.score") { value(notNullValue()) }
        }
        gradeDraft("""{"score":7.25}""").andExpect { status { isBadRequest() } }
        gradeDraft("""{"score":7}""", token = bob).andExpect { status { isNotFound() } }
        gradeDraft("""{"score":7}""", token = maria).andExpect { status { isForbidden() } }
    }
}
