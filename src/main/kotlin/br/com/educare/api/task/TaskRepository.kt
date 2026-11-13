package br.com.educare.api.task

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

/** Scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface TaskRepository : JpaRepository<Task, Long>, JpaSpecificationExecutor<Task>
