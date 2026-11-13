package br.com.educare.api.review

import br.com.educare.api.flashcard.FlashcardProgressResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ReviewController(private val service: ReviewService) {

    @GetMapping("/api/reviews/queue")
    @PreAuthorize("hasRole('STUDENT')")
    fun queue(): QueueResponse = service.queue()

    @PostMapping("/api/reviews")
    @PreAuthorize("hasRole('STUDENT')")
    fun rate(@Valid @RequestBody request: RateRequest): RateResponse = service.rate(request)

    @GetMapping("/api/reviews/progress")
    @PreAuthorize("hasRole('STUDENT')")
    fun progress(): FlashcardProgressResponse = service.progress()
}
