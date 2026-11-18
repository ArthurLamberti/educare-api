package br.com.educare.api.assignment

import br.com.educare.api.level.LevelResponse
import br.com.educare.api.level.toResponse
import br.com.educare.api.submission.Submission
import br.com.educare.api.task.QuestionView
import br.com.educare.api.task.TaskCategoryResponse
import br.com.educare.api.task.toResponse
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
    /** The task's level, if any. */
    val level: LevelResponse?,
    /** The task's category, if any (the teacher's own). */
    val category: TaskCategoryResponse?,
    /** The task's current questions; once submitted, answer those in `submission.answers`. */
    val questions: List<QuestionView>,
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
    /** The student's private draft: only to that student, while one is saved. */
    val draft: AnswerDraftView?,
    /** The teacher's unpublished correction: only to the task's teacher, while one is saved. */
    val gradeDraft: GradeDraftView?,
    /** The task's teacher has an unpublished correction (always false for others). */
    val hasGradeDraft: Boolean,
)

data class AnswerDraftView(
    val answer: String,
    /** Draft answers to the questions, by position (may be blank). */
    val answers: List<String>,
    val savedAt: Instant,
)

data class GradeDraftView(
    val score: BigDecimal?,
    val feedback: String?,
    val savedAt: Instant,
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
    /** For tasks with questions: each prompt as the student saw it, with their answer, in order. */
    val answers: List<AnswerView>,
)

data class AnswerView(val prompt: String, val answer: String)

data class GradeView(
    val score: BigDecimal,
    val feedback: String,
    val gradedAt: Instant,
    val gradedByName: String?,
)

private fun Submission.toView() = SubmissionView(
    answer, instructionsSnapshot, submittedAt, updatedAt, late, version, answers.map { AnswerView(it.prompt, it.answer) },
)

private fun Submission.toGrade(): GradeView? {
    val score = score ?: return null
    return GradeView(score, feedback.orEmpty(), requireNotNull(gradedAt), gradedBy?.name)
}

/**
 * [submission] is this assignment's submission, if any (see SubmissionRepository); [viewerId] is the
 * user reading it, who decides which private draft is included.
 */
fun Assignment.toResponse(now: Instant, submission: Submission?, viewerId: Long): AssignmentResponse {
    val isStudent = student.user.id == viewerId
    val isTeacher = task.teacher.id == viewerId
    return AssignmentResponse(
        id = requireNotNull(id),
        taskId = requireNotNull(task.id),
        taskTitle = task.title,
        instructions = task.instructions,
        level = task.level?.toResponse(),
        category = task.category?.toResponse(),
        questions = task.questions.map { QuestionView(it) },
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
        draft = answerDraftSavedAt?.takeIf { isStudent }?.let { AnswerDraftView(answerDraft.orEmpty(), answerDraftAnswers, it) },
        gradeDraft = gradeDraftSavedAt?.takeIf { isTeacher }?.let { GradeDraftView(gradeDraftScore, gradeDraftFeedback, it) },
        hasGradeDraft = isTeacher && hasGradeDraft,
    )
}
