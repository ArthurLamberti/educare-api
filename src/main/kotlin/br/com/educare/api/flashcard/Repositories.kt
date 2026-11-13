package br.com.educare.api.flashcard

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

/** All scoped to the current school by Hibernate (see TenantOwnedEntity). */
interface DeckRepository : JpaRepository<Deck, Long>, JpaSpecificationExecutor<Deck> {

    /** Serializes card changes (positions) on one deck. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deck d where d.id = :id")
    fun findForUpdate(id: Long): Deck?
}

interface CardRepository : JpaRepository<Card, Long> {

    fun findByDeckIdOrderByPosition(deckId: Long): List<Card>

    fun countByDeckId(deckId: Long): Long

    @Query("select coalesce(max(c.position), -1) from Card c where c.deck.id = :deckId")
    fun maxPosition(deckId: Long): Int

    /** Card counts per deck, for the decks of one page. */
    @Query("select c.deck.id, count(c) from Card c where c.deck.id in :deckIds group by c.deck.id")
    fun countByDeckIds(deckIds: Collection<Long>): List<Array<Any>>
}

interface DeckStudentRepository : JpaRepository<DeckStudent, DeckStudentId> {

    @EntityGraph(attributePaths = ["student", "student.user"])
    fun findByDeckIdOrderByStudentUserName(deckId: Long): List<DeckStudent>

    fun existsByDeckIdAndStudentUserId(deckId: Long, userId: Long): Boolean

    @Query("select d.deck.id, count(d) from DeckStudent d where d.deck.id in :deckIds group by d.deck.id")
    fun countByDeckIds(deckIds: Collection<Long>): List<Array<Any>>
}
