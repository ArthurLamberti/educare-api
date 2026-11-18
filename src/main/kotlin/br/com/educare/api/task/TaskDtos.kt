package br.com.educare.api.task

import br.com.educare.api.level.LevelResponse
import br.com.educare.api.level.toResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class TaskRequest(
    @field:NotBlank @field:Size(max = 200)
    val title: String,

    /** Required unless the task has questions. */
    @field:Size(max = 10000)
    val instructions: String = "",

    /** Optional; an active level (see GET /api/levels). */
    val levelId: Long? = null,

    /** Optional; one of the teacher's categories (see /api/task-categories). */
    val categoryId: Long? = null,

    /** Open questions in order; the whole list replaces the current one. */
    @field:Size(max = 50) @field:Valid
    val questions: List<QuestionRequest>? = null,
)

data class QuestionRequest(
    @field:NotBlank @field:Size(max = 2000)
    val prompt: String,
)

/** A question of a task; answers refer to it by its position in the list. */
data class QuestionView(val prompt: String)

data class TaskResponse(
    val id: Long,
    val title: String,
    val instructions: String,
    val level: LevelResponse?,
    val category: TaskCategoryResponse?,
    val questions: List<QuestionView>,
    val archived: Boolean,
    /** Assignments of this task in any status; a task with assignments cannot be deleted. */
    val assignmentCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Task.toResponse(assignmentCount: Long) = TaskResponse(
    id = requireNotNull(id),
    title = title,
    instructions = instructions,
    level = level?.toResponse(),
    category = category?.toResponse(),
    questions = questions.map { QuestionView(it) },
    archived = archived,
    assignmentCount = assignmentCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
