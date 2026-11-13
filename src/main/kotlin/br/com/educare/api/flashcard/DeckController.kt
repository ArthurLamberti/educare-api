package br.com.educare.api.flashcard

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
class DeckController(private val service: DeckService) {

    /** Teachers: own decks; students: decks proposed to them; admins: all. Newest first. */
    @GetMapping("/api/decks")
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt", "id"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(defaultValue = "false") includeArchived: Boolean,
    ): Page<DeckResponse> = service.list(includeArchived, pageable)

    @GetMapping("/api/decks/{id}")
    fun get(@PathVariable id: Long): DeckResponse = service.get(id)

    @PostMapping("/api/decks")
    @PreAuthorize("hasRole('TEACHER')")
    fun create(@Valid @RequestBody request: DeckRequest): ResponseEntity<DeckResponse> {
        val created = service.create(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id).toUri()
        return ResponseEntity.created(location).body(created)
    }

    @PutMapping("/api/decks/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: DeckRequest): DeckResponse = service.update(id, request)

    @PostMapping("/api/decks/{id}/archive")
    @PreAuthorize("hasRole('TEACHER')")
    fun archive(@PathVariable id: Long): DeckResponse = service.setArchived(id, true)

    @PostMapping("/api/decks/{id}/unarchive")
    @PreAuthorize("hasRole('TEACHER')")
    fun unarchive(@PathVariable id: Long): DeckResponse = service.setArchived(id, false)

    // ---- cards

    /** In deck order. */
    @GetMapping("/api/decks/{id}/cards")
    fun cards(@PathVariable id: Long): List<CardResponse> = service.cards(id)

    @PostMapping("/api/decks/{id}/cards")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCard(@PathVariable id: Long, @Valid @RequestBody request: CardRequest): CardResponse = service.addCard(id, request)

    @PutMapping("/api/decks/{id}/cards/order")
    @PreAuthorize("hasRole('TEACHER')")
    fun reorder(@PathVariable id: Long, @Valid @RequestBody request: ReorderCardsRequest): List<CardResponse> =
        service.reorder(id, requireNotNull(request.cardIds))

    @PutMapping("/api/cards/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    fun updateCard(@PathVariable id: Long, @Valid @RequestBody request: CardRequest): CardResponse = service.updateCard(id, request)

    @DeleteMapping("/api/cards/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCard(@PathVariable id: Long) = service.deleteCard(id)

    // ---- students

    @GetMapping("/api/decks/{id}/students")
    @PreAuthorize("hasRole('TEACHER')")
    fun students(@PathVariable id: Long): List<DeckStudentResponse> = service.students(id)

    @PostMapping("/api/decks/{id}/students")
    @PreAuthorize("hasRole('TEACHER')")
    fun addStudents(@PathVariable id: Long, @Valid @RequestBody request: DeckStudentsRequest): List<DeckStudentResponse> =
        service.addStudents(id, request)

    @DeleteMapping("/api/decks/{id}/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeStudent(@PathVariable id: Long, @PathVariable studentId: Long) = service.removeStudent(id, studentId)
}
