package br.com.educare.api.lesson

import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * A new word of a lesson, listed by its teacher. It can be sent once to the student's flashcards
 * ([flashcardSentAt]) and once to a sentence-writing task ([taskSentAt]).
 */
@Entity
@Table(name = "lesson_words")
class LessonWord(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    val lesson: Lesson,

    @Column(nullable = false, length = 200)
    val word: String,

    @Column(nullable = false, length = 1000)
    val meaning: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    var flashcardSentAt: Instant? = null
        protected set

    var taskSentAt: Instant? = null
        protected set

    fun sentToFlashcards(at: Instant) {
        flashcardSentAt = at
    }

    fun sentToTask(at: Instant) {
        taskSentAt = at
    }
}

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface LessonWordRepository : JpaRepository<LessonWord, Long> {
    fun findByLessonIdOrderById(lessonId: Long): List<LessonWord>

    fun countByLessonId(lessonId: Long): Long
}

data class LessonWordRequest(
    @field:NotBlank @field:Size(max = 200)
    val word: String,

    @field:NotBlank @field:Size(max = 1000)
    val meaning: String,
)

/** Words added at once, in this order; all are saved or none. */
data class LessonWordBatchRequest(
    @field:NotEmpty @field:Size(max = LessonWordService.MAX_WORDS) @field:Valid
    val words: List<LessonWordRequest>?,
)

/** Sends the words not yet in a task as a sentence-writing task for the lesson's student. */
data class LessonWordTaskRequest(
    /** Optional; must be in the future. */
    val dueAt: Instant? = null,
)

data class LessonWordResponse(
    val id: Long,
    val word: String,
    val meaning: String,
    val createdAt: Instant,
    /** When the word was sent to the student's flashcards; null if not yet. */
    val flashcardSentAt: Instant?,
    /** When the word was sent in a sentence-writing task; null if not yet. */
    val taskSentAt: Instant?,
)

fun LessonWord.toResponse() = LessonWordResponse(
    id = requireNotNull(id),
    word = word,
    meaning = meaning,
    createdAt = createdAt,
    flashcardSentAt = flashcardSentAt,
    taskSentAt = taskSentAt,
)

/** The task made from a lesson's words, assigned to its student. */
data class LessonWordTaskResponse(
    val taskId: Long,
    val assignmentId: Long,
    val words: List<LessonWordResponse>,
)
