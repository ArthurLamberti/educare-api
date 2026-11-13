package br.com.educare.api.flashcard

import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
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

/** A teacher's set of flashcards. */
@Entity
@Table(name = "decks")
class Deck(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    val teacher: User,

    title: String,
    description: String?,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 200)
    var title: String = title
        protected set

    @Column(length = 2000)
    var description: String? = description
        protected set

    /** Hidden from the default list and not assignable; students keep reviewing it. */
    @Column(nullable = false)
    var archived: Boolean = false
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun edit(title: String, description: String?) {
        this.title = title
        this.description = description
        touch()
    }

    fun changeArchived(archived: Boolean) {
        this.archived = archived
        touch()
    }

    fun touch() {
        updatedAt = Instant.now()
    }
}
