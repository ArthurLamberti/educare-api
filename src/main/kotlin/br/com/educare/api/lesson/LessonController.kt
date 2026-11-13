package br.com.educare.api.lesson

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.Instant

@RestController
@RequestMapping("/api/lessons")
class LessonController(private val service: LessonService) {

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["startsAt", "id"]) pageable: Pageable,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(required = false) status: LessonStatus?,
        @RequestParam(required = false) studentId: Long?,
        @RequestParam(required = false) teacherId: Long?,
    ): Page<LessonResponse> = service.list(LessonFilter(from, to, status, studentId, teacherId), pageable)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): LessonResponse = service.get(id)

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    fun create(@Valid @RequestBody request: CreateLessonRequest): ResponseEntity<LessonResponse> {
        val created = service.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateLessonRequest): LessonResponse =
        service.update(id, request)

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TEACHER')")
    fun cancel(@PathVariable id: Long): LessonResponse = service.cancel(id)
}
