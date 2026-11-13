package br.com.educare.api.flashcard

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class FlashcardController(private val service: FlashcardService) {

    /** The student's cards in creation order: their teacher, admins and the student themself. */
    @GetMapping("/api/students/{studentId}/cards")
    fun cardsOf(@PathVariable studentId: Long): List<CardResponse> = service.cardsOf(studentId)

    @GetMapping("/api/cards")
    @PreAuthorize("hasRole('STUDENT')")
    fun myCards(): List<CardResponse> = service.myCards()

    @PostMapping("/api/students/{studentId}/cards")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@PathVariable studentId: Long, @Valid @RequestBody request: CardRequest): CardResponse = service.add(studentId, request)

    @PostMapping("/api/students/{studentId}/cards/batch")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun addAll(@PathVariable studentId: Long, @Valid @RequestBody request: CardBatchRequest): List<CardResponse> =
        service.addAll(studentId, requireNotNull(request.cards))

    @PutMapping("/api/cards/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: CardRequest): CardResponse = service.update(id, request)

    @DeleteMapping("/api/cards/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = service.delete(id)

    @GetMapping("/api/flashcards/progress")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    fun progress(): List<FlashcardProgressResponse> = service.progress()
}
