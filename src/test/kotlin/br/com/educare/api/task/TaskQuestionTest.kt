package br.com.educare.api.task

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Change 18: tasks made of open questions, answered one by one. */
class TaskQuestionTest : IntegrationTest() {

    lateinit var ana: String
    lateinit var maria: String
    var mariaId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        createTeacher("alpha", "ana@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        maria = tokenFor("alpha", "maria@alpha.com")
    }

    private fun questions(vararg prompts: String) = prompts.joinToString(",", "[", "]") { """{"prompt":"$it"}""" }

    private fun createTask(body: String) = mockMvc.post("/api/tasks") {
        auth("alpha", ana)
        json(body)
    }

    private fun id(body: String) = jsonMapper.readTree(body).get("id").asLong()

    /** A task with three questions and no instructions, assigned to Maria; returns the assignment id. */
    private fun assigned(): Pair<Long, Long> {
        val taskId = id(createTask("""{"title":"Quiz","questions":${questions("Where do you live?", "What do you do?", "Why English?")}}""")
            .andReturn().response.contentAsString)
        val body = mockMvc.post("/api/tasks/$taskId/assignments") {
            auth("alpha", ana)
            json("""{"studentIds":[$mariaId]}""")
        }.andReturn().response.contentAsString
        return taskId to jsonMapper.readTree(body).get(0).get("id").asLong()
    }

    private fun submit(id: Long, body: String) = mockMvc.put("/api/assignments/$id/submission") {
        auth("alpha", maria)
        json(body)
    }

    @Test
    fun `a task made of questions needs no instructions`() {
        createTask("""{"title":"Quiz","questions":${questions("  One?  ", "Two?", "Three?")}}""").andExpect {
            status { isCreated() }
            jsonPath("$.instructions") { value("") }
            jsonPath("$.questions[*].prompt") { value(contains("One?", "Two?", "Three?")) }
        }
        createTask("""{"title":"Empty"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.instructions") { value(notNullValue()) }
        }
        createTask("""{"title":"Blank","questions":${questions(" ")}}""").andExpect { status { isBadRequest() } }
        createTask("""{"title":"Long","questions":${questions(*Array(51) { "Q$it?" })}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.questions") { value(notNullValue()) }
        }
    }

    @Test
    fun `editing a task replaces its questions`() {
        val taskId = id(createTask("""{"title":"Quiz","instructions":"Read.","questions":${questions("A?", "B?")}}""").andReturn().response.contentAsString)
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Quiz","instructions":"Read.","questions":${questions("C?")}}""")
        }.andExpect { jsonPath("$.questions[*].prompt") { value(contains("C?")) } }
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Quiz","instructions":"Read."}""")
        }.andExpect { jsonPath("$.questions", hasSize<Any>(0)) }
    }

    @Test
    fun `the student answers every question and the submission keeps the prompts`() {
        val (_, id) = assigned()
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect {
            jsonPath("$.questions[*].prompt") { value(contains("Where do you live?", "What do you do?", "Why English?")) }
        }
        submit(id, """{"answers":["Porto Alegre"," I teach ","For work"]}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.submission.answer") { value("") }
            jsonPath("$.submission.answers[1].prompt") { value("What do you do?") }
            jsonPath("$.submission.answers[*].answer") { value(contains("Porto Alegre", "I teach", "For work")) }
        }
        mockMvc.get("/api/assignments/$id") { auth("alpha", ana) }.andExpect {
            jsonPath("$.submission.answers", hasSize<Any>(3))
        }
    }

    @Test
    fun `every question must be answered`() {
        val (_, id) = assigned()
        submit(id, """{"answers":["Porto Alegre","I teach"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.answers") { value("Answer each of the 3 questions") }
        }
        submit(id, """{"answers":["Porto Alegre"," ","For work"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.answers") { value("Answer every question") }
        }
        submit(id, """{"answers":["a","b","${"x".repeat(5001)}"]}""").andExpect { status { isBadRequest() } }
        submit(id, """{"answer":"Just text"}""").andExpect { status { isBadRequest() } }
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect { jsonPath("$.status") { value("ASSIGNED") } }
    }

    @Test
    fun `editing the task after a submission does not change it, and resubmissions answer the copied questions`() {
        val (taskId, id) = assigned()
        submit(id, """{"answers":["a","b","c"]}""")
        val version = jsonMapper.readTree(mockMvc.get("/api/assignments/$id") { auth("alpha", ana) }.andReturn().response.contentAsString)
            .get("submission").get("version").asLong()
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Quiz","questions":${questions("Only one now?")}}""")
        }
        mockMvc.get("/api/assignments/$id") { auth("alpha", maria) }.andExpect {
            jsonPath("$.submission.answers[*].prompt") { value(contains("Where do you live?", "What do you do?", "Why English?")) }
        }
        submit(id, """{"answers":["x"]}""").andExpect { status { isBadRequest() } }
        submit(id, """{"answers":["a","b","c, edited"]}""").andExpect {
            status { isOk() }
            jsonPath("$.submission.answers[2].answer") { value("c, edited") }
            jsonPath("$.submission.answers[2].prompt") { value("Why English?") }
        }
        // Changing an answer changes the version the teacher grades against.
        mockMvc.put("/api/assignments/$id/grade") {
            auth("alpha", ana)
            json("""{"score":8,"feedback":"ok","version":$version}""")
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `a draft may leave questions blank`() {
        val (_, id) = assigned()
        mockMvc.put("/api/assignments/$id/draft") {
            auth("alpha", maria)
            json("""{"answers":["Porto Alegre","",""]}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ASSIGNED") }
            jsonPath("$.draft.answers") { value(contains("Porto Alegre", "", "")) }
            jsonPath("$.draft.answer") { value("") }
        }
        submit(id, """{"answers":["Porto Alegre","I teach","For work"]}""").andExpect {
            jsonPath("$.draft") { value(null as Any?) }
        }
    }

    @Test
    fun `tasks without questions work as before`() {
        val taskId = id(createTask("""{"title":"Essay","instructions":"Write."}""").andReturn().response.contentAsString)
        val body = mockMvc.post("/api/tasks/$taskId/assignments") {
            auth("alpha", ana)
            json("""{"studentIds":[$mariaId]}""")
        }.andReturn().response.contentAsString
        val id = jsonMapper.readTree(body).get(0).get("id").asLong()
        submit(id, """{"answers":["x"]}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.answers") { value("This task has no questions") }
        }
        submit(id, """{"answer":" "}""").andExpect { jsonPath("$.errors.answer") { value(notNullValue()) } }
        submit(id, """{"answer":"My essay"}""").andExpect {
            status { isOk() }
            jsonPath("$.submission.answers", hasSize<Any>(0))
        }
    }
}
