package br.com.educare.api.task

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class TaskRequest(
    @field:NotBlank @field:Size(max = 200)
    val title: String,

    @field:NotBlank @field:Size(max = 10000)
    val instructions: String,
)

data class TaskResponse(
    val id: Long,
    val title: String,
    val instructions: String,
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
    archived = archived,
    assignmentCount = assignmentCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
