package br.com.educare.api.flashcard

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.student.StudentRepository
import br.com.educare.api.user.Role
import br.com.educare.api.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Flashcard decks. Teachers author their own decks and propose them to their students; students read
 * the decks proposed to them; admins read all. Decks the caller may not see are reported as 404.
 */
@Service
@Transactional(readOnly = true)
class DeckService(
    private val decks: DeckRepository,
    private val cards: CardRepository,
    private val deckStudents: DeckStudentRepository,
    private val students: StudentRepository,
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val clock: Clock,
) {

    // ---- decks

    fun list(includeArchived: Boolean, pageable: Pageable): Page<DeckResponse> {
        // Students keep seeing archived decks they have, so they can keep reviewing them.
        val hideArchived = !includeArchived && currentUser.role != Role.STUDENT
        val spec = visibleTo().and { root, _, cb -> if (hideArchived) cb.isFalse(root.get("archived")) else cb.conjunction() }
        val page = decks.findAll(spec, pageable)
        val ids = page.content.mapNotNull { it.id }
        if (ids.isEmpty()) return page.map { it.toResponse(0, null) }
        val cardCounts = cards.countByDeckIds(ids).toCounts()
        val studentCounts = if (currentUser.role == Role.STUDENT) null else deckStudents.countByDeckIds(ids).toCounts()
        return page.map { it.toResponse(cardCounts[it.id] ?: 0, studentCounts?.let { counts -> counts[it.id] ?: 0 }) }
    }

    fun get(id: Long): DeckResponse = response(findVisible(id))

    @Transactional
    fun create(request: DeckRequest): DeckResponse {
        val teacher = users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        val deck = decks.saveAndFlush(Deck(teacher, request.title.trim(), request.description.clean()))
        return deck.toResponse(0, 0)
    }

    @Transactional
    fun update(id: Long, request: DeckRequest): DeckResponse {
        val deck = findOwn(id)
        deck.edit(request.title.trim(), request.description.clean())
        return response(deck)
    }

    @Transactional
    fun setArchived(id: Long, archived: Boolean): DeckResponse {
        val deck = findOwn(id)
        deck.changeArchived(archived)
        return response(deck)
    }

    // ---- cards

    fun cards(deckId: Long): List<CardResponse> =
        cards.findByDeckIdOrderByPosition(requireNotNull(findVisible(deckId).id)).map { it.toResponse() }

    /** Appended last. */
    @Transactional
    fun addCard(deckId: Long, request: CardRequest): CardResponse {
        val deck = findOwn(deckId, lock = true)
        if (cards.countByDeckId(deckId) >= MAX_CARDS) throw ConflictException("A deck can hold at most $MAX_CARDS cards")
        val card = Card(deck, request.front.trim(), request.back.trim(), cards.maxPosition(deckId) + 1)
        deck.touch()
        return cards.saveAndFlush(card).toResponse()
    }

    @Transactional
    fun updateCard(cardId: Long, request: CardRequest): CardResponse {
        val card = findOwnCard(cardId)
        card.edit(request.front.trim(), request.back.trim())
        card.deck.touch()
        return card.toResponse()
    }

    @Transactional
    fun deleteCard(cardId: Long) {
        val card = findOwnCard(cardId)
        card.deck.touch()
        cards.delete(card)
    }

    /** [ids] must be exactly the deck's cards, each once; positions become 0..n-1. */
    @Transactional
    fun reorder(deckId: Long, ids: List<Long>): List<CardResponse> {
        val deck = findOwn(deckId, lock = true)
        val current = cards.findByDeckIdOrderByPosition(deckId)
        if (ids.size != current.size || ids.toSet() != current.mapNotNull { it.id }.toSet()) {
            throw FieldValidationException("cardIds", "must list every card of the deck exactly once")
        }
        val byId = current.associateBy { it.id }
        ids.forEachIndexed { index, id -> byId.getValue(id).moveTo(index) }
        deck.touch()
        cards.flush()
        return ids.map { byId.getValue(it).toResponse() }
    }

    // ---- students

    fun students(deckId: Long): List<DeckStudentResponse> =
        deckStudents.findByDeckIdOrderByStudentUserName(requireNotNull(findOwn(deckId).id)).map { it.toResponse() }

    /** Idempotent: students who already have the deck are skipped. All must be the teacher's active students. */
    @Transactional
    fun addStudents(deckId: Long, request: DeckStudentsRequest): List<DeckStudentResponse> {
        val deck = findOwn(deckId)
        if (deck.archived) throw ConflictException("Archived decks cannot be proposed to students")
        val ids = requireNotNull(request.studentIds).distinct()
        val found = students.findAllById(ids).filter { it.teacher.id == currentUser.id && it.user.active }
        if (found.size != ids.size) throw FieldValidationException("studentIds", "must all be your active students")
        val existing = deckStudents.findByDeckIdOrderByStudentUserName(deckId).map { it.student.id }.toSet()
        val now = clock.instant()
        deckStudents.saveAllAndFlush(found.filter { it.id !in existing }.map { DeckStudent(deck, it, now) })
        return students(deckId)
    }

    /** Hides the deck from the student; their review history is kept. */
    @Transactional
    fun removeStudent(deckId: Long, studentId: Long) {
        findOwn(deckId)
        deckStudents.findByIdOrNull(DeckStudentId(deckId, studentId))?.let { deckStudents.delete(it) }
    }

    // ---- helpers

    private fun response(deck: Deck): DeckResponse {
        val id = requireNotNull(deck.id)
        val studentCount = if (currentUser.role == Role.STUDENT) null else deckStudents.countByDeckIds(listOf(id)).toCounts()[id] ?: 0
        return deck.toResponse(cards.countByDeckId(id), studentCount)
    }

    private fun findOwn(id: Long, lock: Boolean = false): Deck =
        (if (lock) decks.findForUpdate(id) else decks.findByIdOrNull(id))
            ?.takeIf { it.teacher.id == currentUser.id }
            ?: throw NotFoundException("Deck $id not found")

    private fun findOwnCard(id: Long): Card =
        cards.findByIdOrNull(id)?.takeIf { it.deck.teacher.id == currentUser.id }
            ?: throw NotFoundException("Card $id not found")

    private fun findVisible(id: Long): Deck =
        decks.findOne(visibleTo().and { root, _, cb -> cb.equal(root.get<Long>("id"), id) }).orElse(null)
            ?: throw NotFoundException("Deck $id not found")

    /** Teachers: their decks. Students: decks proposed to them. Admins: all of the school. */
    private fun visibleTo(): Specification<Deck> {
        val userId = currentUser.id
        return when (currentUser.role) {
            Role.ADMIN -> Specification.unrestricted()
            Role.TEACHER -> Specification { root, _, cb -> cb.equal(root.get<Any>("teacher").get<Long>("id"), userId) }
            Role.STUDENT -> Specification { root, query, cb ->
                val sub = requireNotNull(query).subquery(Long::class.java)
                val ds = sub.from(DeckStudent::class.java)
                sub.select(cb.literal(1L)).where(
                    cb.equal(ds.get<Deck>("deck"), root),
                    cb.equal(ds.get<Any>("student").get<Any>("user").get<Long>("id"), userId),
                )
                cb.exists(sub)
            }
        }
    }

    private fun List<Array<Any>>.toCounts(): Map<Long, Long> = associate { (it[0] as Long) to (it[1] as Long) }

    private fun String?.clean() = this?.trim()?.ifEmpty { null }

    companion object {
        const val MAX_CARDS = 1000
    }
}
