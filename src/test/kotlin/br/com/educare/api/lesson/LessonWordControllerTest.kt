package br.com.educare.api.lesson

import br.com.educare.api.support.IntegrationTest
import br.com.educare.api.support.auth
import br.com.educare.api.support.json
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class LessonWordControllerTest : IntegrationTest() {

    lateinit var admin: String
    lateinit var ana: String
    lateinit var bob: String
    lateinit var maria: String
    var mariaId = 0L
    var lessonId = 0L

    private val startsAt: Instant = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(13))

    @BeforeEach
    fun school() {
        provisionSchool("alpha")
        admin = tokenFor("alpha")
        createTeacher("alpha", "ana@alpha.com")
        createTeacher("alpha", "bob@alpha.com")
        ana = tokenFor("alpha", "ana@alpha.com")
        bob = tokenFor("alpha", "bob@alpha.com")
        mariaId = createStudent("alpha", ana, "maria@alpha.com", name = "Maria")
        maria = tokenFor("alpha", "maria@alpha.com")
        val body = mockMvc.post("/api/lessons") {
            auth("alpha", ana)
            json("""{"studentId":$mariaId,"startsAt":"$startsAt","durationMinutes":60,"topic":"Past simple"}""")
        }.andReturn().response.contentAsString
        lessonId = jsonMapper.readTree(body).get("id").asLong()
    }

    private fun add(token: String, words: String) = mockMvc.post("/api/lessons/$lessonId/words") {
        auth("alpha", token)
        json("""{"words":$words}""")
    }

    @Test
    fun `teacher lists new words, and the student and admins read them`() {
        add(ana, """[{"word":" went ","meaning":"foi"},{"word":"bought","meaning":"comprou"}]""").andExpect {
            status { isCreated() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].word") { value("went") }
            jsonPath("$[0].flashcardSentAt") { value(nullValue()) }
            jsonPath("$[0].taskSentAt") { value(nullValue()) }
        }
        add(ana, """[{"word":"saw","meaning":"viu"}]""").andExpect { jsonPath("$[2].word") { value("saw") } }

        listOf(ana, maria, admin).forEach { token ->
            mockMvc.get("/api/lessons/$lessonId/words") { auth("alpha", token) }.andExpect {
                status { isOk() }
                jsonPath("$", hasSize<Any>(3))
            }
        }
        mockMvc.get("/api/lessons/$lessonId/words") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        add(bob, """[{"word":"x","meaning":"y"}]""").andExpect { status { isNotFound() } }
        add(maria, """[{"word":"x","meaning":"y"}]""").andExpect { status { isForbidden() } }
        add(ana, """[{"word":"ran","meaning":" "}]""").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors['words[0].meaning']") { exists() }
        }

        val id = jsonMapper.readTree(mockMvc.get("/api/lessons/$lessonId/words") { auth("alpha", ana) }
            .andReturn().response.contentAsString)[0].get("id").asLong()
        mockMvc.delete("/api/lesson-words/$id") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/lesson-words/$id") { auth("alpha", ana) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/lessons/$lessonId/words") { auth("alpha", ana) }.andExpect { jsonPath("$", hasSize<Any>(2)) }
    }

    @Test
    fun `words go to the student's flashcards once each`() {
        add(ana, """[{"word":"went","meaning":"foi"},{"word":"bought","meaning":"comprou"}]""")
        mockMvc.post("/api/lessons/$lessonId/words/flashcards") { auth("alpha", ana) }.andExpect {
            status { isOk() }
            jsonPath("$[0].flashcardSentAt") { value(notNullValue()) }
            jsonPath("$[1].flashcardSentAt") { value(notNullValue()) }
        }
        mockMvc.get("/api/cards") { auth("alpha", maria) }.andExpect {
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].front") { value("went") }
            jsonPath("$[0].back") { value("foi") }
        }

        // Nothing new: refused. A word added later is the only one sent next time.
        mockMvc.post("/api/lessons/$lessonId/words/flashcards") { auth("alpha", ana) }.andExpect { status { isConflict() } }
        add(ana, """[{"word":"saw","meaning":"viu"}]""")
        mockMvc.post("/api/lessons/$lessonId/words/flashcards") { auth("alpha", ana) }.andExpect { status { isOk() } }
        mockMvc.get("/api/cards") { auth("alpha", maria) }.andExpect {
            jsonPath("$", hasSize<Any>(3))
            jsonPath("$[2].front") { value("saw") }
        }
        mockMvc.post("/api/lessons/$lessonId/words/flashcards") { auth("alpha", bob) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `words not yet in a task become a sentence-writing task for the student`() {
        add(ana, """[{"word":"went","meaning":"foi"},{"word":"look_up","meaning":"procurar"}]""")
        val dueAt = startsAt.plus(Duration.ofDays(2))
        mockMvc.post("/api/lessons/$lessonId/words/task") {
            auth("alpha", ana)
            json("""{"dueAt":"$dueAt"}""")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.taskId") { exists() }
            jsonPath("$.assignmentId") { exists() }
            jsonPath("$.words[0].taskSentAt") { value(notNullValue()) }
            jsonPath("$.words[0].flashcardSentAt") { value(nullValue()) }
        }

        mockMvc.get("/api/assignments") { auth("alpha", maria) }.andExpect {
            jsonPath("$.content", hasSize<Any>(1))
            jsonPath("$.content[0].taskTitle") { value("Frases com as palavras novas: Past simple") }
            jsonPath("$.content[0].instructions") { value(containsString("Escreva uma frase com cada uma das palavras novas")) }
            jsonPath("$.content[0].instructions") { value(containsString("- **went**: foi")) }
            jsonPath("$.content[0].instructions") { value(containsString("- **look\\_up**: procurar")) }
            jsonPath("$.content[0].dueAt") { value(dueAt.toString()) }
        }

        // Already sent: refused; a past due date is refused and sends nothing.
        mockMvc.post("/api/lessons/$lessonId/words/task") { auth("alpha", ana); json("{}") }.andExpect { status { isConflict() } }
        add(ana, """[{"word":"saw","meaning":"viu"}]""")
        mockMvc.post("/api/lessons/$lessonId/words/task") {
            auth("alpha", ana)
            json("""{"dueAt":"2020-01-01T00:00:00Z"}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.dueAt") { exists() }
        }
        mockMvc.get("/api/lessons/$lessonId/words") { auth("alpha", ana) }.andExpect {
            jsonPath("$[2].taskSentAt") { value(nullValue()) }
        }
        mockMvc.post("/api/lessons/$lessonId/words/task") { auth("alpha", ana); json("{}") }.andExpect {
            status { isCreated() }
            jsonPath("$.words[2].taskSentAt") { value(notNullValue()) }
        }
        mockMvc.get("/api/assignments") { auth("alpha", maria) }.andExpect { jsonPath("$.content", hasSize<Any>(2)) }
    }
}
