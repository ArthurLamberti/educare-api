package br.com.educare.api.submission

import br.com.educare.api.assignment.AssignmentResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SubmissionController(private val service: SubmissionService) {

    /** Submit or replace the answer (until graded). */
    @PutMapping("/api/assignments/{id}/submission")
    @PreAuthorize("hasRole('STUDENT')")
    fun submit(@PathVariable id: Long, @Valid @RequestBody request: SubmitAnswerRequest): AssignmentResponse =
        service.submit(id, request)

    /** Grade or revise the grade. */
    @PutMapping("/api/assignments/{id}/grade")
    @PreAuthorize("hasRole('TEACHER')")
    fun grade(@PathVariable id: Long, @Valid @RequestBody request: GradeRequest): AssignmentResponse =
        service.grade(id, request)
}
