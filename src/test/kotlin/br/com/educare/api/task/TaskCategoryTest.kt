package br.com.educare.api.task

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/** Change 17: each teacher's own task categories. */
class TaskCategoryTest : IntegrationTest() {

    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    var mariaId = 0L

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        maria = tokenFor("alpha", "maria@alpha.com")
    }

    private fun category(name: String, token: String = ana) = mockMvc.post("/api/task-categories") {
        auth("alpha", token)
        json("""{"name":"$name"}""")
    }

    private fun task(title: String, categoryId: Long?, token: String = ana) = mockMvc.post("/api/tasks") {
        auth("alpha", token)
        json("""{"title":"$title","instructions":"Do it."${categoryId?.let { ""","categoryId":$it""" } ?: ""}}""")
    }

    private fun id(body: String) = jsonMapper.readTree(body).get("id").asLong()

    @Test
    fun `a teacher creates, lists, renames and deletes their categories`() {
        val grammar = id(category("  Grammar ").andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Grammar") }
        }.andReturn().response.contentAsString)
        category("listening")
        category("Exam prep")
        task("Verbs", grammar)
        task("More verbs", grammar)

        mockMvc.get("/api/task-categories") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$[*].name") { value(contains("Exam prep", "Grammar", "listening")) }
            jsonPath("$[1].taskCount") { value(2) }
            jsonPath("$[0].taskCount") { value(0) }
        }
        mockMvc.put("/api/task-categories/$grammar") {
            auth("alpha", ana)
            json("""{"name":"Grammar & usage"}""")
        }.andExpect { jsonPath("$.name") { value("Grammar & usage") } }
        // Changing only the case of its own name is allowed.
        mockMvc.put("/api/task-categories/$grammar") {
            auth("alpha", ana)
            json("""{"name":"GRAMMAR & USAGE"}""")
        }.andExpect { status { isOk() } }

        mockMvc.delete("/api/task-categories/$grammar") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/tasks") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content", hasSize<Any>(2))
            jsonPath("$.content[0].category") { value(nullValue()) }
            jsonPath("$.content[1].category") { value(nullValue()) }
        }
    }

    @Test
    fun `names are unique per teacher ignoring case and spaces, not across teachers`() {
        category("Grammar")
        category(" grammar ").andExpect { status { isConflict() } }
        val vocab = id(category("Vocabulary").andReturn().response.contentAsString)
        mockMvc.put("/api/task-categories/$vocab") {
            auth("alpha", ana)
            json("""{"name":"GRAMMAR"}""")
        }.andExpect { status { isConflict() } }
        category("Grammar", token = bob).andExpect { status { isCreated() } }
        category(" ").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.name") { value(notNullValue()) }
        }
        category("x".repeat(61)).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a teacher has at most 100 categories`() {
        repeat(100) { category("C$it").andExpect { status { isCreated() } } }
        category("One more").andExpect { status { isConflict() } }
    }

    @Test
    fun `categories of other teachers are not found and cannot be used`() {
        val bobs = id(category("Bob's", token = bob).andReturn().response.contentAsString)
        mockMvc.put("/api/task-categories/$bobs") {
            auth("alpha", ana)
            json("""{"name":"Mine"}""")
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/task-categories/$bobs") { auth("alpha", ana) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/task-categories") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(0)) }
        task("Sneaky", bobs).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.categoryId") { value(notNullValue()) }
        }
        category("x", token = maria).andExpect { status { isForbidden() } }
    }

    @Test
    fun `tasks and assignments carry and filter by category`() {
        val listening = id(category("Listening").andReturn().response.contentAsString)
        val reading = id(category("Reading").andReturn().response.contentAsString)
        val l = id(task("Podcast", listening).andExpect {
            jsonPath("$.category.name") { value("Listening") }
        }.andReturn().response.contentAsString)
        val r = id(task("Article", reading).andReturn().response.contentAsString)
        task("Loose", null).andExpect { jsonPath("$.category") { value(nullValue()) } }

        mockMvc.get("/api/tasks?categoryId=$listening") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].title") { value(contains("Podcast")) }
        }
        listOf(l, r).forEach { t ->
            mockMvc.post("/api/tasks/$t/assignments") {
                auth("alpha", ana)
                json("""{"studentIds":[$mariaId]}""")
            }
        }
        mockMvc.get("/api/assignments?categoryId=$reading") { auth("alpha", ana) }.andExpect {
            jsonPath("$.content[*].taskTitle") { value(contains("Article")) }
            jsonPath("$.content[0].category.name") { value("Reading") }
        }
        // Editing moves the task to another category or none.
        mockMvc.put("/api/tasks/$l") {
            auth("alpha", ana)
            json("""{"title":"Podcast","instructions":"Do it.","categoryId":$reading}""")
        }.andExpect { jsonPath("$.category.name") { value("Reading") } }
        mockMvc.put("/api/tasks/$l") {
            auth("alpha", ana)
            json("""{"title":"Podcast","instructions":"Do it."}""")
        }.andExpect { jsonPath("$.category") { value(nullValue()) } }
    }
}
