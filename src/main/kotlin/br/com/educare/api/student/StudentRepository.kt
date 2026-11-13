package br.com.educare.api.student

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface StudentRepository : JpaRepository<Student, Long> {

    @Query(
        value = """
            select s from Student s join fetch s.user u join fetch s.teacher t
            where (:teacherId is null or t.id = :teacherId)
              and (:includeInactive = true or u.active = true)
        """,
        countQuery = """
            select count(s) from Student s join s.user u join s.teacher t
            where (:teacherId is null or t.id = :teacherId)
              and (:includeInactive = true or u.active = true)
        """,
    )
    fun search(teacherId: Long?, includeInactive: Boolean, pageable: Pageable): Page<Student>

    fun findByUserId(userId: Long): Student?

    /** Locks the student so their ratings apply one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.user.id = :userId")
    fun findByUserIdForUpdate(userId: Long): Student?
}
