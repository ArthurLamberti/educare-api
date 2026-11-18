package br.com.educare.api.task

import br.com.educare.api.level.Level
import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
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
    level: Level? = null,
    category: TaskCategory? = null,

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

    /** Optional level of the scale (see Level). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    var level: Level? = level
        protected set

    /** Optional category of the teacher's own (see TaskCategory). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: TaskCategory? = category
        protected set

    /** Open questions, in order (see [replaceQuestions]); answers refer to them by position. */
    @ElementCollection
    @CollectionTable(name = "task_questions", joinColumns = [JoinColumn(name = "task_id")])
    @OrderColumn(name = "position")
    @Column(name = "prompt", nullable = false, length = 2000)
    @BatchSize(size = 50)
    private var questionPrompts: MutableList<String> = mutableListOf()

    val questions: List<String> get() = questionPrompts.toList()

    fun replaceQuestions(prompts: List<String>) {
        questionPrompts.clear()
        questionPrompts.addAll(prompts)
    }

    /** Archived tasks are hidden from the default list and cannot be assigned. */
    @Column(nullable = false)
    var archived: Boolean = false
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun edit(title: String, instructions: String, level: Level?, category: TaskCategory?) {
        this.title = title
        this.instructions = instructions
        this.level = level
        this.category = category
        updatedAt = Instant.now()
    }

    fun changeArchived(archived: Boolean) {
        this.archived = archived
        updatedAt = Instant.now()
    }
}
