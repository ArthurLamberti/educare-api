package br.com.educare.api.submission

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface SubmissionRepository : JpaRepository<Submission, Long> {

    @EntityGraph(attributePaths = ["gradedBy"])
    fun findByAssignmentId(assignmentId: Long): Submission?

    /** Submissions of one page of assignments (one query). */
    @EntityGraph(attributePaths = ["gradedBy"])
    fun findByAssignmentIdIn(assignmentIds: Collection<Long>): List<Submission>
}
