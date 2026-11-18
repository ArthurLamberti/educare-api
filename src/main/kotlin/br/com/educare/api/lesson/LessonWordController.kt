package br.com.educare.api.lesson

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class LessonWordController(private val service: LessonWordService) {

    /** The lesson's teacher and student, and the school's admins. */
    @GetMapping("/api/lessons/{id}/words")
    fun list(@PathVariable id: Long): List<LessonWordResponse> = service.list(id)

    @PostMapping("/api/lessons/{id}/words")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@PathVariable id: Long, @Valid @RequestBody request: LessonWordBatchRequest): List<LessonWordResponse> =
        service.add(id, requireNotNull(request.words))

    @DeleteMapping("/api/lesson-words/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)

    @PostMapping("/api/lessons/{id}/words/flashcards")
    @PreAuthorize("hasRole('TEACHER')")
    fun sendToFlashcards(@PathVariable id: Long): LessonWordFlashcardsResponse = service.sendToFlashcards(id)

    @PostMapping("/api/lessons/{id}/words/task")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendAsTask(@PathVariable id: Long, @Valid @RequestBody request: LessonWordTaskRequest): LessonWordTaskResponse =
        service.sendAsTask(id, request)
}
