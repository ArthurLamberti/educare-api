package br.com.educare.api.task

import br.com.educare.api.assignment.AssignmentRepository
import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The current teacher's task library. Other teachers' tasks are reported as 404. */
@Service
@Transactional(readOnly = true)
class TaskService(
    private val tasks: TaskRepository,
    private val assignments: AssignmentRepository,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
) {

    fun list(q: String?, includeArchived: Boolean, pageable: Pageable): Page<TaskResponse> {
        val teacherId = currentUser.id
        val spec = Specification<Task> { root, _, cb ->
            val predicates = buildList {
                add(cb.equal(root.get<Any>("teacher").get<Long>("id"), teacherId))
                if (!includeArchived) add(cb.isFalse(root.get("archived")))
                q?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add(cb.like(cb.lower(root.get("title")), "%${escapeLike(it.lowercase())}%", '\\'))
                }
            }
            cb.and(*predicates.toTypedArray())
        }
        val page = tasks.findAll(spec, pageable)
        val ids = page.content.mapNotNull { it.id }
        val counts = if (ids.isEmpty()) emptyMap() else
            assignments.countByTaskIds(ids).associate { (it[0] as Long) to (it[1] as Long) }
        return page.map { it.toResponse(counts[it.id] ?: 0) }
    }

    fun get(id: Long): TaskResponse = findOwn(id).let { it.toResponse(assignments.countByTaskId(id)) }

    @Transactional
    fun create(request: TaskRequest): TaskResponse {
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        val task = Task(teacher = teacher, title = request.title.trim(), instructions = request.instructions.trim())
        return tasks.saveAndFlush(task).toResponse(0)
    }

    @Transactional
    fun update(id: Long, request: TaskRequest): TaskResponse {
        val task = findOwn(id)
        task.edit(request.title.trim(), request.instructions.trim())
        return task.toResponse(assignments.countByTaskId(id))
    }

    @Transactional
    fun setArchived(id: Long, archived: Boolean): TaskResponse {
        val task = findOwn(id)
        task.changeArchived(archived)
        return task.toResponse(assignments.countByTaskId(id))
    }

    /** Only unused tasks are deleted; tasks with assignments are archived instead. */
    @Transactional
    fun delete(id: Long) {
        val task = findOwn(id)
        if (assignments.existsByTaskId(id)) {
            throw ConflictException("This task has assignments and cannot be deleted; archive it instead")
        }
        tasks.delete(task)
    }

    /** The current teacher's task, or 404. */
    fun findOwn(id: Long): Task =
        tasks.findByIdOrNull(id)?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Task $id not found")

    private fun escapeLike(value: String) = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
