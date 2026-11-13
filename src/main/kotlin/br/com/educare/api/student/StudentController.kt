package br.com.educare.api.student

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
class StudentController(private val service: StudentService) {

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["name"]) pageable: Pageable,
        @RequestParam(required = false) teacherId: Long?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
    ): Page<StudentResponse> = service.list(pageable, teacherId, includeInactive)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): StudentResponse = service.get(id)

    @PostMapping
    fun create(@Valid @RequestBody request: CreateStudentRequest): ResponseEntity<StudentResponse> {
        val created = service.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateStudentRequest): StudentResponse =
        service.update(id, request)

    /** Deactivates: the student loses access but keeps their history. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.setActive(id, false)

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun activate(@PathVariable id: Long) = service.setActive(id, true)
}
