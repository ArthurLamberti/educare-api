package br.com.educare.api.flashcard

import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/** One flashcard. Edits keep the id, so review progress (change 09) stays with the card. */
@Entity
@Table(name = "cards")
class Card(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false, updatable = false)
    val deck: Deck,

    front: String,
    back: String,
    position: Int,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 1000)
    var front: String = front
        protected set

    @Column(nullable = false, length = 1000)
    var back: String = back
        protected set

    @Column(nullable = false)
    var position: Int = position
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun edit(front: String, back: String) {
        this.front = front
        this.back = back
        updatedAt = Instant.now()
    }

    fun moveTo(position: Int) {
        this.position = position
    }
}
