package br.com.educare.api.assignment

import br.com.educare.api.submission.Submission
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.math.BigDecimal
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
    /** Present once submitted. */
    val submission: SubmissionView?,
    /** Present once graded. */
    val grade: GradeView?,
)

data class SubmissionView(
    val answer: String,
    /** The task instructions when the student first submitted. */
    val instructionsSnapshot: String,
    val submittedAt: Instant,
    val updatedAt: Instant,
    val late: Boolean,
    /** Send it back with a grade so it never applies to an answer the teacher did not see. */
    val version: Long,
)

data class GradeView(
    val score: BigDecimal,
    val feedback: String,
    val gradedAt: Instant,
    val gradedByName: String?,
)

private fun Submission.toView() = SubmissionView(answer, instructionsSnapshot, submittedAt, updatedAt, late, version)

private fun Submission.toGrade(): GradeView? {
    val score = score ?: return null
    return GradeView(score, feedback.orEmpty(), requireNotNull(gradedAt), gradedBy?.name)
}

/** [submission] is this assignment's submission, if any (see SubmissionRepository). */
fun Assignment.toResponse(now: Instant, submission: Submission?) = AssignmentResponse(
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
    submission = submission?.toView(),
    // Only graded work shows a grade (to the student and everyone else).
    grade = submission?.takeIf { status == AssignmentStatus.GRADED }?.toGrade(),
)
