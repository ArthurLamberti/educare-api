package br.com.educare.api.review

import org.springframework.data.jpa.repository.JpaRepository

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface ReviewEventRepository : JpaRepository<ReviewEvent, Long>
