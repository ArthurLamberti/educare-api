package br.com.educare.api.lesson

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/lesson-series")
class LessonSeriesController(private val service: LessonSeriesService) {

    @GetMapping
    fun list(): List<LessonSeriesResponse> = service.list()

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateLessonSeriesRequest): CreateLessonSeriesResponse = service.create(request)

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('TEACHER')")
    fun end(@PathVariable id: Long, @RequestBody(required = false) request: EndLessonSeriesRequest?): EndLessonSeriesResponse =
        service.end(id, request ?: EndLessonSeriesRequest())
}
