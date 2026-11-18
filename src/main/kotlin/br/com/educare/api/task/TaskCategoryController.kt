package br.com.educare.api.task

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/task-categories")
@PreAuthorize("hasRole('TEACHER')")
class TaskCategoryController(private val service: TaskCategoryService) {

    @GetMapping
    fun list(): List<TaskCategoryListItem> = service.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: TaskCategoryRequest): TaskCategoryResponse = service.create(request)

    @PutMapping("/{id}")
    fun rename(@PathVariable id: Long, @Valid @RequestBody request: TaskCategoryRequest): TaskCategoryResponse =
        service.rename(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)
}
