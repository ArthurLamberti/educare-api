package br.com.educare.api.task

import br.com.educare.api.level.LevelResponse
import br.com.educare.api.level.toResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class TaskRequest(
    @field:NotBlank @field:Size(max = 200)
    val title: String,

    @field:NotBlank @field:Size(max = 10000)
    val instructions: String,

    /** Optional; an active level (see GET /api/levels). */
    val levelId: Long? = null,

    /** Optional; one of the teacher's categories (see /api/task-categories). */
    val categoryId: Long? = null,
)

data class TaskResponse(
    val id: Long,
    val title: String,
    val instructions: String,
    val level: LevelResponse?,
    val category: TaskCategoryResponse?,
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
    archived = archived,
    assignmentCount = assignmentCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
