package br.com.educare.api.assignment

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class AssignTaskRequest(
    @field:NotEmpty @field:Size(max = 200)
    val studentIds: List<Long>?,

    /** Optional; must be in the future. */
    val dueAt: Instant? = null,
)

data class AssignmentResponse(
    val id: Long,
    val taskId: Long,
    val taskTitle: String,
    val instructions: String,
    val teacherId: Long,
    val teacherName: String,
    val studentId: Long,
    val studentName: String,
    val dueAt: Instant?,
    val assignedAt: Instant,
    val status: AssignmentStatus,
    val overdue: Boolean,
)

fun Assignment.toResponse(now: Instant) = AssignmentResponse(
    id = requireNotNull(id),
    taskId = requireNotNull(task.id),
    taskTitle = task.title,
    instructions = task.instructions,
    teacherId = requireNotNull(task.teacher.id),
    teacherName = task.teacher.name,
    studentId = requireNotNull(student.id),
    studentName = student.user.name,
    dueAt = dueAt,
    assignedAt = assignedAt,
    status = status,
    overdue = isOverdue(now),
)
