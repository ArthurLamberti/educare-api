package br.com.educare.api.reschedule

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class RescheduleRequestController(private val service: RescheduleRequestService) {

    @PostMapping("/api/lessons/{lessonId}/reschedule-requests")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable lessonId: Long, @Valid @RequestBody request: CreateRescheduleRequest): RescheduleRequestResponse =
        service.create(lessonId, request)

    /** Newest first. */
    @GetMapping("/api/reschedule-requests")
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt", "id"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) status: RescheduleStatus?,
    ): Page<RescheduleRequestResponse> = service.list(status, pageable)

    @PostMapping("/api/reschedule-requests/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    fun withdraw(@PathVariable id: Long): RescheduleRequestResponse = service.withdraw(id)

    @PostMapping("/api/reschedule-requests/{id}/approve")
    @PreAuthorize("hasRole('TEACHER')")
    fun approve(@PathVariable id: Long): RescheduleRequestResponse = service.approve(id)

    @PostMapping("/api/reschedule-requests/{id}/reject")
    @PreAuthorize("hasRole('TEACHER')")
    fun reject(@PathVariable id: Long, @Valid @RequestBody(required = false) body: RejectRescheduleRequest?): RescheduleRequestResponse =
        service.reject(id, body ?: RejectRescheduleRequest())
}
