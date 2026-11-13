package br.com.educare.api.assignment

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AssignmentController(private val service: AssignmentService) {

    @PostMapping("/api/tasks/{taskId}/assignments")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun assign(@PathVariable taskId: Long, @Valid @RequestBody request: AssignTaskRequest): List<AssignmentResponse> =
        service.assign(taskId, request)

    /**
     * Soonest due first (PostgreSQL sorts missing due dates last in ascending order), then by assignment date.
     * `status` may be repeated (`status=SUBMITTED&status=GRADED`).
     */
    @GetMapping("/api/assignments")
    fun list(
        @PageableDefault(size = 20, sort = ["dueAt", "assignedAt", "id"]) pageable: Pageable,
        @RequestParam(required = false) status: List<AssignmentStatus>?,
        @RequestParam(required = false) studentId: Long?,
        @RequestParam(required = false) taskId: Long?,
    ): Page<AssignmentResponse> = service.list(AssignmentFilter(status.orEmpty(), studentId, taskId), pageable)

    @GetMapping("/api/assignments/{id}")
    fun get(@PathVariable id: Long): AssignmentResponse = service.get(id)

    @DeleteMapping("/api/assignments/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unassign(@PathVariable id: Long) = service.unassign(id)
}
