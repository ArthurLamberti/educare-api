package br.com.educare.api.lesson

import br.com.educare.api.assignment.AssignTaskRequest
import br.com.educare.api.assignment.AssignmentService
import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.flashcard.CardRequest
import br.com.educare.api.flashcard.FlashcardService
import br.com.educare.api.task.TaskRequest
import br.com.educare.api.task.TaskService
import br.com.educare.api.tenant.TenantContext
import br.com.educare.api.tenant.TenantDirectory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The new words of a lesson. Its teacher lists them and sends them, to the student's flashcards and as a
 * sentence-writing task; its student and the school's admins read them. Each word is sent at most once to
 * each place, so sending again only sends the words added since.
 */
@Service
@Transactional(readOnly = true)
class LessonWordService(
    private val words: LessonWordRepository,
    private val lessons: LessonRepository,
    private val lessonService: LessonService,
    private val flashcards: FlashcardService,
    private val tasks: TaskService,
    private val assignments: AssignmentService,
    private val tenants: TenantDirectory,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    fun list(lessonId: Long): List<LessonWordResponse> {
        lessonService.findVisible(lessonId)
        return words.findByLessonIdOrderById(lessonId).map { it.toResponse() }
    }

    /** Adds [requests] after the lesson's words, all or nothing; returns the whole list. */
    @Transactional
    fun add(lessonId: Long, requests: List<LessonWordRequest>): List<LessonWordResponse> {
        val lesson = ownLesson(lessonId)
        val count = words.countByLessonId(lessonId)
        if (count + requests.size > MAX_WORDS) {
            throw ConflictException("A lesson can have at most $MAX_WORDS words; it has $count")
        }
        val now = clock.instant()
        words.saveAllAndFlush(requests.map { LessonWord(lesson, it.word.trim(), it.meaning.trim(), now) })
        return words.findByLessonIdOrderById(lessonId).map { it.toResponse() }
    }

    /** Removes the word from the list; cards and tasks already sent are kept. */
    @Transactional
    fun delete(wordId: Long) {
        val word = words.findByIdOrNull(wordId)?.takeIf { it.lesson.teacher.id == currentUser.id }
            ?: throw NotFoundException("Word $wordId not found")
        words.delete(word)
    }

    /** One flashcard (word / meaning) per word not yet sent, for the lesson's student; returns the whole list. */
    @Transactional
    fun sendToFlashcards(lessonId: Long): LessonWordFlashcardsResponse {
        val lesson = ownLesson(lessonId)
        val all = words.findByLessonIdOrderById(lessonId)
        val pending = all.filter { it.flashcardSentAt == null }
        if (pending.isEmpty()) throw ConflictException("All words of this lesson are already in the student's flashcards")
        // Words the student already has as cards are skipped but still count as sent.
        val result = flashcards.addNew(requireNotNull(lesson.student.id), pending.map { CardRequest(it.word, it.meaning) })
        val now = clock.instant()
        pending.forEach { it.sentToFlashcards(now) }
        return LessonWordFlashcardsResponse(words = all.map { it.toResponse() }, skipped = result.skipped)
    }

    /**
     * A task asking the student to write a sentence with each word not yet sent in a task, created in the
     * teacher's library and assigned to the lesson's student (optional due date).
     */
    @Transactional
    fun sendAsTask(lessonId: Long, request: LessonWordTaskRequest): LessonWordTaskResponse {
        val lesson = ownLesson(lessonId)
        val all = words.findByLessonIdOrderById(lessonId)
        val pending = all.filter { it.taskSentAt == null }
        if (pending.isEmpty()) throw ConflictException("All words of this lesson were already sent in a task")

        val task = tasks.create(TaskRequest(taskTitle(lesson), taskInstructions(lesson, pending)))
        val assignment = assignments.assign(task.id, AssignTaskRequest(listOf(requireNotNull(lesson.student.id)), request.dueAt)).single()
        val now = clock.instant()
        pending.forEach { it.sentToTask(now) }
        return LessonWordTaskResponse(task.id, assignment.id, all.map { it.toResponse() })
    }

    private fun ownLesson(id: Long): Lesson =
        lessons.findByIdOrNull(id)?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Lesson $id not found")

    // Task text is content for the student, in the school's language (like the e-mails).
    private fun taskTitle(lesson: Lesson) = "Frases com as palavras novas: ${lesson.topic}".take(200)

    private fun taskInstructions(lesson: Lesson, list: List<LessonWord>): String {
        val day = DATE.format(lesson.startsAt.atZone(zone()))
        val items = list.joinToString("\n") { "- **${escape(it.word)}**: ${escape(it.meaning)}" }
        return "Escreva uma frase com cada uma das palavras novas da aula de $day (${escape(lesson.topic)}).\n\n$items"
    }

    private fun zone(): ZoneId {
        val tenant = tenants.findById(requireNotNull(TenantContext.get())) ?: error("Unknown school")
        return ZoneId.of(tenant.timeZone)
    }

    /** Words are shown as Markdown in the task; their own symbols stay literal. */
    private fun escape(text: String) = text.replace(MARKDOWN, "\\\\$0").replace(Regex("\\s+"), " ")

    companion object {
        const val MAX_WORDS = 200
        private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val MARKDOWN = Regex("""[\\`*_\[\]#<>|~]""")
    }
}
