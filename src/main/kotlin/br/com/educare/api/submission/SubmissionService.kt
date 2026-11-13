package br.com.educare.api.submission

import br.com.educare.api.assignment.Assignment
import br.com.educare.api.assignment.AssignmentRepository
import br.com.educare.api.assignment.AssignmentResponse
import br.com.educare.api.assignment.AssignmentStatus
import br.com.educare.api.assignment.toResponse
import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Answers and grades. Allowed transitions:
 *
 * | action | from                | to        | who                    |
 * |--------|---------------------|-----------|------------------------|
 * | submit | ASSIGNED, SUBMITTED | SUBMITTED | the assigned student   |
 * | grade  | SUBMITTED, GRADED   | GRADED    | the task's teacher     |
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

    fun submit(assignmentId: Long, request: SubmitAnswerRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.student.user.id == currentUser.id }
        val answer = request.answer.trim()
        val submission = when (assignment.status) {
            AssignmentStatus.ASSIGNED -> {
                val created = Submission(
                    assignment = assignment,
                    answer = answer,
                    instructionsSnapshot = assignment.task.instructions,
                    submittedAt = now,
                    late = assignment.dueAt?.isBefore(now) == true,
                )
                assignment.submitted()
                submissions.save(created)
            }
            AssignmentStatus.SUBMITTED -> current(assignment).also { it.editAnswer(answer, now) }
            AssignmentStatus.GRADED -> throw ConflictException("Graded work can no longer be changed")
        }
        assignments.flush()
        return assignment.toResponse(now, submission)
    }

    fun grade(assignmentId: Long, request: GradeRequest): AssignmentResponse {
        val now = clock.instant()
        val assignment = lock(assignmentId) { it.task.teacher.id == currentUser.id }
        if (assignment.status == AssignmentStatus.ASSIGNED) throw ConflictException("The student has not submitted an answer yet")
        val submission = current(assignment)
        if (request.version != null && request.version != submission.version) {
            throw ConflictException("The student changed the answer after you opened it; reload to review the new answer")
        }
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        submission.grade(requireNotNull(request.score), request.feedback.trim(), teacher, now)
        assignment.graded()
        assignments.flush()
        return assignment.toResponse(now, submission)
    }

    private fun current(assignment: Assignment): Submission =
        submissions.findByAssignmentId(requireNotNull(assignment.id)) ?: error("Assignment ${assignment.id} has no submission")

    /** Row lock: a student's edit and a grade on the same assignment run one after the other. */
    private fun lock(id: Long, allowed: (Assignment) -> Boolean): Assignment =
        assignments.findForUpdate(id)?.takeIf(allowed) ?: throw NotFoundException("Assignment $id not found")
}
