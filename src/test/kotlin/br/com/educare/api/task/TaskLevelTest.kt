package br.com.educare.api.task

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Change 16: the level scale is data and tasks may be tagged with one level. */
class TaskLevelTest : IntegrationTest() {

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

    private fun levelId(code: String): Long = jdbc.queryForObject("select id from levels where code = ?", Long::class.java, code)!!

    private fun task(title: String, levelId: Long?) = mockMvc.post("/api/tasks") {
        auth("alpha", ana)
        json("""{"title":"$title","instructions":"Do it."${levelId?.let { ""","levelId":$it""" } ?: ""}}""")
    }

    private fun id(body: String) = jsonMapper.readTree(body).get("id").asLong()

    @Test
    fun `the scale is A1 to C2 in order, for any signed-in user`() {
        listOf(ana, maria).forEach { token ->
            mockMvc.get("/api/levels") { auth("alpha", token) }.andExpect {
                status { isOk() }
                jsonPath("$[*].code") { value(contains("A1", "A2", "B1", "B2", "C1", "C2")) }
                jsonPath("$[2].name") { value("Intermediário") }
            }
        }
    }

    @Test
    fun `a task carries its level, also in its assignments`() {
        val b1 = levelId("B1")
        val taskId = id(task("Past tense", b1).andExpect {
            status { isCreated() }
            jsonPath("$.level.code") { value("B1") }
            jsonPath("$.level.id") { value(b1) }
        }.andReturn().response.contentAsString)
        task("No level", null).andExpect { jsonPath("$.level") { value(nullValue()) } }

        mockMvc.post("/api/tasks/$taskId/assignments") {
            auth("alpha", ana)
            json("""{"studentIds":[$mariaId]}""")
        }.andExpect { jsonPath("$[0].level.code") { value("B1") } }
        mockMvc.get("/api/assignments") { auth("alpha", maria) }.andExpect { jsonPath("$.content[0].level.code") { value("B1") } }

        // Editing changes or clears the level.
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Past tense","instructions":"Do it.","levelId":${levelId("C1")}}""")
        }.andExpect { jsonPath("$.level.code") { value("C1") } }
        mockMvc.put("/api/tasks/$taskId") {
            auth("alpha", ana)
            json("""{"title":"Past tense","instructions":"Do it."}""")
        }.andExpect { jsonPath("$.level") { value(nullValue()) } }
    }

    @Test
    fun `unknown and inactive levels are refused, but an inactive level stays on its tasks`() {
        task("Bad", 999_999).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.levelId") { value(notNullValue()) }
        }
        val a1 = levelId("A1")
        val taskId = id(task("Basics", a1).andReturn().response.contentAsString)
        jdbc.update("update levels set active = false where code = 'A1'")
        try {
            mockMvc.get("/api/levels") { auth("alpha", ana) }.andExpect { jsonPath("$[0].code") { value("A2") } }
            task("New", a1).andExpect { status { isBadRequest() } }
            // Editing other fields of a task keeps its (now inactive) level.
            mockMvc.put("/api/tasks/$taskId") {
                auth("alpha", ana)
                json("""{"title":"Basics 2","instructions":"Do it.","levelId":$a1}""")
            }.andExpect {
                status { isOk() }
                jsonPath("$.level.code") { value("A1") }
            }
        } finally {
            jdbc.update("update levels set active = true where code = 'A1'")
        }
    }

    @Test
    fun `tasks and assignments filter by level`() {
        val a2 = levelId("A2")
        val a2Task = id(task("A2 task", a2).andReturn().response.contentAsString)
        val b2Task = id(task("B2 task", levelId("B2")).andReturn().response.contentAsString)
        task("No level", null)
        mockMvc.get("/api/tasks?levelId=$a2") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("A2 task")) }
        }
        listOf(a2Task, b2Task).forEach { t ->
            mockMvc.post("/api/tasks/$t/assignments") {
                auth("alpha", ana)
                json("""{"studentIds":[$mariaId]}""")
            }
        }
        mockMvc.get("/api/assignments?levelId=$a2") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].taskTitle") { value(contains("A2 task")) }
        }
    }
}
