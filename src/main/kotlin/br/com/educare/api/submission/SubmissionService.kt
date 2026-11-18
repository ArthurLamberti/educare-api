package br.com.educare.api.submission

import br.com.educare.api.assignment.Assignment
import br.com.educare.api.assignment.AssignmentRepository
import br.com.educare.api.assignment.AssignmentResponse
import br.com.educare.api.assignment.AssignmentStatus
import br.com.educare.api.assignment.toResponse
import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Answers and grades. Allowed transitions:
 *
 * | action      | from                | to        | who                    |
 * |-------------|---------------------|-----------|------------------------|
 * | draft       | ASSIGNED, SUBMITTED | unchanged | the assigned student   |
 * | submit      | ASSIGNED, SUBMITTED | SUBMITTED | the assigned student   |
 * | grade draft | SUBMITTED, GRADED   | unchanged | the task's teacher     |
 * | grade       | SUBMITTED, GRADED   | GRADED    | the task's teacher     |
 *
 * Drafts are private: the student's is never shown to the teacher and the teacher's never to the
 * student. Submitting clears the student's draft; grading clears the teacher's.
 *
 * Assignments the caller cannot act on are reported as 404.
 */
@Service
@Transactional
class SubmissionService(
    private val assignments: AssignmentRepository,
    private val submissions: SubmissionRepository,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    fun saveDraft(assignmentId: Long, request: AnswerDraftRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.student.user.id == currentUser.id }
        if (assignment.status == AssignmentStatus.GRADED) throw ConflictException("Graded work can no longer be changed")
        val answers = request.answers.orEmpty()
        if (answers.any { it.length > MAX_QUESTION_ANSWER }) throw tooLong()
        assignment.saveAnswerDraft(request.answer.orEmpty(), answers, now)
        assignments.flush()
        return assignment.toResponse(now, submissions.findByAssignmentId(assignmentId), currentUser.id)
    }

    fun submit(assignmentId: Long, request: SubmitAnswerRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.student.user.id == currentUser.id }
        val answer = request.answer?.trim().orEmpty()
        val answers = request.answers.orEmpty().map { it.trim() }
        val submission = when (assignment.status) {
            AssignmentStatus.ASSIGNED -> {
                val prompts = assignment.task.questions
                validate(prompts, answer, answers)
                val created = Submission(
                    assignment = assignment,
                    answer = answer,
                    instructionsSnapshot = assignment.task.instructions,
                    submittedAt = now,
                    late = assignment.dueAt?.isBefore(now) == true,
                )
                if (prompts.isNotEmpty()) created.answerQuestions(prompts, answers, now)
                assignment.submitted()
                submissions.save(created)
            }
            AssignmentStatus.SUBMITTED -> current(assignment).also {
                // Later submissions answer the questions copied at the first one.
                val prompts = it.answers.map { a -> a.prompt }
                validate(prompts, answer, answers)
                it.editAnswer(answer, now)
                if (prompts.isNotEmpty()) it.answerQuestions(prompts, answers, now)
            }
            AssignmentStatus.GRADED -> throw ConflictException("Graded work can no longer be changed")
        }
        assignment.clearAnswerDraft()
        assignments.flush()
        return assignment.toResponse(now, submission, currentUser.id)
    }

    fun saveGradeDraft(assignmentId: Long, request: GradeDraftRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.task.teacher.id == currentUser.id }
        val submission = reviewed(assignment, request.version)
        assignment.saveGradeDraft(request.score, request.feedback?.trim()?.ifEmpty { null }, now)
        assignments.flush()
        return assignment.toResponse(now, submission, currentUser.id)
    }

    fun grade(assignmentId: Long, request: GradeRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.task.teacher.id == currentUser.id }
        val submission = reviewed(assignment, request.version)
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        submission.grade(requireNotNull(request.score), request.feedback.trim(), teacher, now)
        assignment.graded()
        assignment.clearGradeDraft()
        assignments.flush()
        return assignment.toResponse(now, submission, currentUser.id)
    }

    /** The submission to grade; 409 before the first answer or when it changed since [version] was read. */
    private fun reviewed(assignment: Assignment, version: Long?): Submission {
        if (assignment.status == AssignmentStatus.ASSIGNED) throw ConflictException("The student has not submitted an answer yet")
        val submission = current(assignment)
        if (version != null && version != submission.version) {
            throw ConflictException("The student changed the answer after you opened it; reload to review the new answer")
        }
        return submission
    }

    /** Without questions the main answer is required; with questions, one non-blank answer per question. */
    private fun validate(prompts: List<String>, answer: String, answers: List<String>) {
        if (prompts.isEmpty()) {
            if (answers.isNotEmpty()) throw FieldValidationException("answers", "This task has no questions")
            if (answer.isBlank()) throw FieldValidationException("answer", "must not be blank")
            return
        }
        if (answers.size != prompts.size) throw FieldValidationException("answers", "Answer each of the ${prompts.size} questions")
        if (answers.any { it.isBlank() }) throw FieldValidationException("answers", "Answer every question")
        if (answers.any { it.length > MAX_QUESTION_ANSWER }) throw tooLong()
    }

    private fun tooLong() = FieldValidationException("answers", "Each answer can have at most $MAX_QUESTION_ANSWER characters")

    private fun current(assignment: Assignment): Submission =
        submissions.findByAssignmentId(requireNotNull(assignment.id)) ?: error("Assignment ${assignment.id} has no submission")

    /** Row lock: a student's edit and a grade on the same assignment run one after the other. */
    private fun lock(id: Long, allowed: (Assignment) -> Boolean): Assignment =
        assignments.findForUpdate(id)?.takeIf(allowed) ?: throw NotFoundException("Assignment $id not found")

    companion object {
        const val MAX_QUESTION_ANSWER = 5000
    }
}
