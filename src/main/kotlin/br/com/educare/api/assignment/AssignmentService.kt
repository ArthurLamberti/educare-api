package br.com.educare.api.assignment

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.submission.Submission
import br.com.educare.api.submission.SubmissionRepository
import br.com.educare.api.task.TaskService
import br.com.educare.api.user.Role
import jakarta.persistence.criteria.JoinType
import org.hibernate.query.criteria.JpaRoot
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

data class AssignmentFilter(
    val statuses: List<AssignmentStatus> = emptyList(),
    val studentId: Long? = null,
    val taskId: Long? = null,
)

/**
 * Tasks given to students. Teachers assign their tasks to their active students and see assignments
 * of their tasks; students see theirs; admins see all. Others are reported as 404.
 */
@Service
@Transactional(readOnly = true)
class AssignmentService(
    private val assignments: AssignmentRepository,
    private val submissions: SubmissionRepository,
    private val students: StudentRepository,
    private val taskService: TaskService,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    /** The grading queue (`status=SUBMITTED` only) is always oldest submission first. */
    fun list(filter: AssignmentFilter, pageable: Pageable): Page<AssignmentResponse> {
        val now = clock.instant()
        val spec = visibleTo().and(filter.toSpec())
        val page = if (filter.statuses == listOf(AssignmentStatus.SUBMITTED)) {
            // Unsorted page: the order comes from the specification (joined submission time).
            assignments.findAll(spec.and(oldestSubmissionFirst()), PageRequest.of(pageable.pageNumber, pageable.pageSize))
        } else {
            assignments.findAll(spec, pageable)
        }
        val byAssignment = page.content.mapNotNull { it.id }.takeIf { it.isNotEmpty() }
            ?.let { ids -> submissions.findByAssignmentIdIn(ids).associateBy { it.assignment.id } }
            .orEmpty()
        return page.map { it.toResponse(now, byAssignment[it.id]) }
    }

    fun toResponse(assignment: Assignment): AssignmentResponse =
        assignment.toResponse(clock.instant(), submissions.findByAssignmentId(requireNotNull(assignment.id)))

    private fun oldestSubmissionFirst() = Specification<Assignment> { root, query, cb ->
        val submission = (root as JpaRoot<Assignment>).join(Submission::class.java)
        submission.on(cb.equal(submission.get<Assignment>("assignment"), root))
        // Spring Data drops this order from the count query.
        query?.orderBy(cb.asc(submission.get<Instant>("submittedAt")), cb.asc(root.get<Long>("id")))
        cb.conjunction()
    }

    fun get(id: Long): AssignmentResponse = toResponse(findVisible(id))

    /** One assignment per student, all or nothing. */
    @Transactional
    fun assign(taskId: Long, request: AssignTaskRequest): List<AssignmentResponse> {
        val now = clock.instant()
        val task = taskService.findOwn(taskId)
        if (task.archived) throw ConflictException("Archived tasks cannot be assigned")
        request.dueAt?.let { if (!it.isAfter(now)) throw FieldValidationException("dueAt", "must be a future date") }

        val ids = requireNotNull(request.studentIds).distinct()
        val found = students.findAllById(ids).filter { it.teacher.id == currentUser.id && it.user.active }
        if (found.size != ids.size) throw FieldValidationException("studentIds", "must all be your active students")

        val open = assignments.findByTaskIdAndStudentIdInAndStatus(taskId, ids, AssignmentStatus.ASSIGNED)
        if (open.isNotEmpty()) {
            val names = open.map { it.student.user.name }.sorted().joinToString(", ")
            throw ConflictException("This task is already assigned to: $names")
        }

        val byId = found.associateBy { it.id }
        val created = ids.map { Assignment(task = task, student = byId.getValue(it), dueAt = request.dueAt, assignedAt = now) }
        return assignments.saveAllAndFlush(created).map { it.toResponse(now, null) }
    }

    /** Only work that has not been submitted can be unassigned. */
    @Transactional
    fun unassign(id: Long) {
        val assignment = assignments.findByIdOrNull(id)
            ?.takeIf { it.task.teacher.id == currentUser.id }
            ?: throw NotFoundException("Assignment $id not found")
        if (assignment.status != AssignmentStatus.ASSIGNED) {
            throw ConflictException("Assignments with a submission cannot be removed")
        }
        assignments.delete(assignment)
    }

    /** Visible to the caller (404 otherwise); used by submissions and grading. */
    fun findVisible(id: Long): Assignment = findVisibleOrNull(id) ?: throw NotFoundException("Assignment $id not found")

    private fun findVisibleOrNull(id: Long): Assignment? =
        assignments.findOne(visibleTo().and { root, _, cb -> cb.equal(root.get<Long>("id"), id) }).orElse(null)

    /** Role scope: teachers see assignments of their tasks, students theirs, admins all of the school. */
    private fun visibleTo(): Specification<Assignment> {
        val userId = currentUser.id
        return when (currentUser.role) {
            Role.ADMIN -> Specification.unrestricted()
            Role.TEACHER -> Specification { root, _, cb ->
                cb.equal(root.join<Any, Any>("task", JoinType.INNER).get<Any>("teacher").get<Long>("id"), userId)
            }
            Role.STUDENT -> Specification { root, _, cb ->
                cb.equal(root.join<Any, Any>("student", JoinType.INNER).get<Any>("user").get<Long>("id"), userId)
            }
        }
    }

    private fun AssignmentFilter.toSpec(): Specification<Assignment> = Specification { root, _, cb ->
        val predicates = buildList {
            if (statuses.isNotEmpty()) add(root.get<AssignmentStatus>("status").`in`(statuses))
            studentId?.takeIf { currentUser.role != Role.STUDENT }
                ?.let { add(cb.equal(root.get<Any>("student").get<Long>("id"), it)) }
            taskId?.let { add(cb.equal(root.get<Any>("task").get<Long>("id"), it)) }
        }
        cb.and(*predicates.toTypedArray())
    }
}
