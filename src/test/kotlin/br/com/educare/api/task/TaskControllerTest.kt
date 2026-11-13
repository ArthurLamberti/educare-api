package br.com.educare.api.task

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

class TaskControllerTest : IntegrationTest() {

    lateinit var ana: String
    lateinit var bob: String
    var mariaId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
    }

    private fun task(token: String = ana, title: String = "Irregular verbs") : Long {
        val body = mockMvc.post("/api/tasks") {
            auth("alpha", token)
            json("""{"title":"$title","instructions":"Write **10** sentences."}""")
        }.andReturn().response.contentAsString
        return jsonMapper.readTree(body).get("id")?.asLong() ?: error("Task creation failed: $body")
    }

    private fun assign(taskId: Long, studentId: Long = mariaId) = mockMvc.post("/api/tasks/$taskId/assignments") {
        auth("alpha", ana)
        json("""{"studentIds":[$studentId]}""")
    }

    @Test
    fun `teacher creates, reads and updates a task`() {
        mockMvc.post("/api/tasks") {
            auth("alpha", ana)
            json("""{"title":"  Irregular verbs ","instructions":"Write **10** sentences."}""")
        }.andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.title") { value("Irregular verbs") }
            jsonPath("$.archived") { value(false) }
            jsonPath("$.assignmentCount") { value(0) }
        }
        mockMvc.put("/api/tasks/1") {
            auth("alpha", ana)
            json("""{"title":"Phrasal verbs","instructions":"New text"}""")
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Phrasal verbs") }
        }
        mockMvc.get("/api/tasks/1") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.instructions") { value("New text") }
        }
    }

    @Test
    fun `invalid task is rejected`() {
        mockMvc.post("/api/tasks") {
            auth("alpha", ana)
            json("""{"title":" ","instructions":"x"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { exists() }
        }
        mockMvc.post("/api/tasks") {
            auth("alpha", ana)
            json("""{"title":"t","instructions":"${"x".repeat(10001)}"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.instructions") { exists() }
        }
    }

    @Test
    fun `another teacher's task is not found`() {
        val id = task(ana)
        mockMvc.get("/api/tasks/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.put("/api/tasks/$id") {
            auth("alpha", bob)
            json("""{"title":"x","instructions":"y"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/tasks/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/tasks") { auth("alpha", bob) }.andExpect { jsonPath("$.totalElements") { value(0) } }
    }

    @Test
    fun `unused task is deleted, task in use is not`() {
        val unused = task(title = "Unused")
        mockMvc.delete("/api/tasks/$unused") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/tasks/$unused") { auth("alpha", ana) }.andExpect { status { isNotFound() } }

        val used = task(title = "Used")
        assign(used).andExpect { status { isCreated() } }
        mockMvc.delete("/api/tasks/$used") { auth("alpha", ana) }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This task has assignments and cannot be deleted; archive it instead") }
        }
        mockMvc.get("/api/tasks/$used") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.assignmentCount") { value(1) }
        }
    }

    @Test
    fun `list is newest first, searchable, and hides archived tasks by default`() {
        val verbs = task(title = "Irregular verbs")
        task(title = "Reading 100% fun")
        val phrasal = task(title = "Phrasal VERBS")
        assign(verbs).andExpect { status { isCreated() } }

        mockMvc.get("/api/tasks") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("Phrasal VERBS", "Reading 100% fun", "Irregular verbs")) }
            jsonPath("$.content[2].assignmentCount") { value(1) }
            jsonPath("$.content[0].assignmentCount") { value(0) }
        }
        mockMvc.get("/api/tasks?q=verbs") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("Phrasal VERBS", "Irregular verbs")) }
        }
        mockMvc.get("/api/tasks") { auth("alpha", ana); param("q", "%") }.andExpect {
            jsonPath("$.content[*].title") { value(contains("Reading 100% fun")) }
        }

        mockMvc.post("/api/tasks/$phrasal/archive") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$.archived") { value(true) }
        }
        mockMvc.get("/api/tasks") { auth("alpha", ana) }.andExpect { jsonPath("$.totalElements") { value(2) } }
        mockMvc.get("/api/tasks?includeArchived=true") { auth("alpha", ana) }.andExpect {
            jsonPath("$.totalElements") { value(3) }
        }
        mockMvc.post("/api/tasks/$phrasal/unarchive") { auth("alpha", ana) }.andExpect {
            jsonPath("$.archived") { value(false) }
        }
        mockMvc.get("/api/tasks") { auth("alpha", ana) }.andExpect { jsonPath("$.totalElements") { value(3) } }
    }

    @Test
    fun `students and admins cannot use the task library`() {
        val maria = tokenFor("alpha", "maria@alpha.com")
        mockMvc.post("/api/tasks") {
            auth("alpha", maria)
            json("""{"title":"x","instructions":"y"}""")
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/tasks") { auth("alpha", maria) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/tasks") { auth("alpha", tokenFor("alpha")) }.andExpect { status { isForbidden() } }
    }
}
