package br.com.educare.api.assignment

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface AssignmentRepository : JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {

    @EntityGraph(attributePaths = ["task", "task.teacher", "student", "student.user"])
    override fun findAll(spec: Specification<Assignment>, pageable: Pageable): Page<Assignment>

    /** Locks the assignment so a submission and a grade on it never interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    fun findForUpdate(id: Long): Assignment?

    fun existsByTaskId(taskId: Long): Boolean

    fun countByTaskId(taskId: Long): Long

    /** Assignment counts per task, for the tasks of one page. */
    @Query("select a.task.id, count(a) from Assignment a where a.task.id in :taskIds group by a.task.id")
    fun countByTaskIds(taskIds: Collection<Long>): List<Array<Any>>

    @EntityGraph(attributePaths = ["student", "student.user"])
    fun findByTaskIdAndStudentIdInAndStatus(taskId: Long, studentIds: Collection<Long>, status: AssignmentStatus): List<Assignment>
}
