package br.com.educare.api.task

import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/** One of a teacher's own categories for their tasks (e.g. Grammar, Listening). */
@Entity
@Table(name = "task_categories")
class TaskCategory(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    val teacher: User,

    name: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 60)
    var name: String = name
        protected set

    fun rename(name: String) {
        this.name = name
    }
}

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface TaskCategoryRepository : JpaRepository<TaskCategory, Long> {

    fun findByTeacherId(teacherId: Long): List<TaskCategory>

    fun countByTeacherId(teacherId: Long): Long

    /** Same name for this teacher, ignoring case. */
    fun findByTeacherIdAndNameIgnoreCase(teacherId: Long, name: String): TaskCategory?

    /** [categoryId, number of tasks] for each category among [ids] that has tasks. */
    @Query("select t.category.id, count(t) from Task t where t.category.id in :ids group by t.category.id")
    fun countTasks(ids: Collection<Long>): List<Array<Any>>
}

data class TaskCategoryRequest(
    @field:NotBlank @field:Size(max = 60)
    val name: String,
)

data class TaskCategoryResponse(val id: Long, val name: String)

/** A category in the teacher's list, with how many tasks use it. */
data class TaskCategoryListItem(val id: Long, val name: String, val taskCount: Long)

fun TaskCategory.toResponse() = TaskCategoryResponse(requireNotNull(id), name)
