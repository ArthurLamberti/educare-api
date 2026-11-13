package br.com.educare.api.review

import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(private val service: ReviewService) {

    @GetMapping("/api/reviews/queue")
    @PreAuthorize("hasRole('STUDENT')")
    fun queue(@RequestParam(required = false) deckId: Long?): QueueResponse = service.queue(deckId)

    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('STUDENT')")
    fun rate(@Valid @RequestBody request: RateRequest): RateResponse = service.rate(request)

    @GetMapping("/api/reviews/progress")
    @PreAuthorize("hasRole('STUDENT')")
    fun studentProgress(): List<DeckProgressResponse> = service.studentProgress()

    @GetMapping("/api/decks/{id}/progress")
    @PreAuthorize("hasRole('TEACHER')")
    fun deckProgress(@PathVariable id: Long): List<StudentProgressResponse> = service.deckProgress(id)
}
