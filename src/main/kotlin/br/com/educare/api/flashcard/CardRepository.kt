package br.com.educare.api.flashcard

import org.springframework.data.jpa.repository.JpaRepository

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface CardRepository : JpaRepository<Card, Long> {

    /** Creation order, which is also the order new cards are introduced in. */
    fun findByStudentIdOrderById(studentId: Long): List<Card>

    fun countByStudentId(studentId: Long): Long
}
