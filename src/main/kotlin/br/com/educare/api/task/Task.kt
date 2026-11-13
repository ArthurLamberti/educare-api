package br.com.educare.api.task

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

/** A teacher's reusable exercise. Instructions are Markdown stored as plain text. */
@Entity
@Table(name = "tasks")
class Task(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false, updatable = false)
    val teacher: User,

    title: String,
    instructions: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 200)
    var title: String = title
        protected set

    @Column(nullable = false, length = 10000)
    var instructions: String = instructions
        protected set

    /** Archived tasks are hidden from the default list and cannot be assigned. */
    @Column(nullable = false)
    var archived: Boolean = false
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun edit(title: String, instructions: String) {
        this.title = title
        this.instructions = instructions
        updatedAt = Instant.now()
    }

    fun changeArchived(archived: Boolean) {
        this.archived = archived
        updatedAt = Instant.now()
    }
}
