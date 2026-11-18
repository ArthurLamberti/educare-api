package br.com.educare.api.task

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** The current teacher's task categories. Other teachers' categories are reported as 404. */
@Service
@Transactional(readOnly = true)
class TaskCategoryService(
    private val categories: TaskCategoryRepository,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    /** Alphabetical (ignoring case), with the number of tasks using each. */
    fun list(): List<TaskCategoryListItem> {
        val own = categories.findByTeacherId(currentUser.id)
        val counts = if (own.isEmpty()) emptyMap() else
            categories.countTasks(own.map { requireNotNull(it.id) }).associate { (it[0] as Long) to (it[1] as Long) }
        return own.sortedBy { it.name.lowercase() }.map { TaskCategoryListItem(requireNotNull(it.id), it.name, counts[it.id] ?: 0) }
    }

    @Transactional
    fun create(request: TaskCategoryRequest): TaskCategoryResponse {
        val name = request.name.trim()
        if (categories.countByTeacherId(currentUser.id) >= MAX_CATEGORIES) {
            throw ConflictException("A teacher can have at most $MAX_CATEGORIES categories")
        }
        requireFreeName(name, except = null)
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        return categories.saveAndFlush(TaskCategory(teacher, name, clock.instant())).toResponse()
    }

    @Transactional
    fun rename(id: Long, request: TaskCategoryRequest): TaskCategoryResponse {
        val category = findOwn(id)
        val name = request.name.trim()
        requireFreeName(name, except = category)
        category.rename(name)
        categories.flush()
        return category.toResponse()
    }

    /** The category's tasks are left without a category (ON DELETE SET NULL). */
    @Transactional
    fun delete(id: Long) {
        categories.delete(findOwn(id))
    }

    /** For a task: null, or one of the current teacher's categories (else 400 on `categoryId`). */
    fun resolve(id: Long?): TaskCategory? = id?.let {
        categories.findByIdOrNull(it)?.takeIf { c -> c.teacher.id == currentUser.id }
            ?: throw FieldValidationException("categoryId", "Unknown category")
    }

    private fun findOwn(id: Long): TaskCategory =
        categories.findByIdOrNull(id)?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Category $id not found")

    private fun requireFreeName(name: String, except: TaskCategory?) {
        val same = categories.findByTeacherIdAndNameIgnoreCase(currentUser.id, name)
        if (same != null && same.id != except?.id) throw ConflictException("You already have a category named \"$name\"")
    }

    companion object {
        const val MAX_CATEGORIES = 100
    }
}
