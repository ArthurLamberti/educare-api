package br.com.educare.api.task

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
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
@RequestMapping("/api/tasks")
@PreAuthorize("hasRole('TEACHER')")
class TaskController(private val service: TaskService) {

    /** Newest first; `q` searches the title. */
    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt", "id"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
    ): Page<TaskResponse> = service.list(q, includeArchived, pageable)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): TaskResponse = service.get(id)

    @PostMapping
    fun create(@Valid @RequestBody request: TaskRequest): ResponseEntity<TaskResponse> {
        val created = service.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: TaskRequest): TaskResponse = service.update(id, request)

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: Long): TaskResponse = service.setArchived(id, true)

    @PostMapping("/{id}/unarchive")
    fun unarchive(@PathVariable id: Long): TaskResponse = service.setArchived(id, false)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
