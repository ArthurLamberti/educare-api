package br.com.educare.api.reschedule

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
interface RescheduleRequestRepository : JpaRepository<RescheduleRequest, Long>, JpaSpecificationExecutor<RescheduleRequest> {

    @EntityGraph(attributePaths = ["lesson", "lesson.teacher", "lesson.student", "lesson.student.user", "decidedBy"])
    override fun findAll(spec: Specification<RescheduleRequest>, pageable: Pageable): Page<RescheduleRequest>

    /** Locks the row so two decisions on the same request cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RescheduleRequest r where r.id = :id")
    fun findForUpdate(id: Long): RescheduleRequest?

    fun existsByLessonIdAndStatus(lessonId: Long, status: RescheduleStatus): Boolean

    fun findByLessonIdAndStatus(lessonId: Long, status: RescheduleStatus): List<RescheduleRequest>
}
