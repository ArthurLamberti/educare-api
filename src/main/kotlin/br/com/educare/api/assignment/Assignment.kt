package br.com.educare.api.assignment

import br.com.educare.api.student.Student
import br.com.educare.api.task.Task
import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/** SUBMITTED and GRADED are reached through submissions (change 07). */
enum class AssignmentStatus { ASSIGNED, SUBMITTED, GRADED }

/** A task given to one student. */
@Entity
@Table(name = "assignments")
class Assignment(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    val task: Task,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    val student: Student,

    @Column(updatable = false)
    val dueAt: Instant?,

    @Column(nullable = false, updatable = false)
    val assignedAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AssignmentStatus = AssignmentStatus.ASSIGNED
        protected set

    /** The student's private draft of the answer; the teacher never sees it. */
    @Column(length = 20000)
    var answerDraft: String? = null
        protected set

    var answerDraftSavedAt: Instant? = null
        protected set

    /** The teacher's private correction (unpublished grade); the student never sees it. */
    @Column(precision = 3, scale = 1)
    var gradeDraftScore: BigDecimal? = null
        protected set

    @Column(length = 10000)
    var gradeDraftFeedback: String? = null
        protected set

    var gradeDraftSavedAt: Instant? = null
        protected set

    val hasGradeDraft: Boolean get() = gradeDraftSavedAt != null

    fun saveAnswerDraft(answer: String, at: Instant) {
        check(status != AssignmentStatus.GRADED) { "Graded work can no longer be changed" }
        answerDraft = answer
        answerDraftSavedAt = at
    }

    fun clearAnswerDraft() {
        answerDraft = null
        answerDraftSavedAt = null
    }

    fun saveGradeDraft(score: BigDecimal?, feedback: String?, at: Instant) {
        check(status != AssignmentStatus.ASSIGNED) { "Nothing to grade" }
        gradeDraftScore = score
        gradeDraftFeedback = feedback
        gradeDraftSavedAt = at
    }

    fun clearGradeDraft() {
        gradeDraftScore = null
        gradeDraftFeedback = null
        gradeDraftSavedAt = null
    }

    /** ASSIGNED → SUBMITTED (first answer, see Submission). */
    fun submitted() {
        check(status == AssignmentStatus.ASSIGNED) { "Only ASSIGNED work can be submitted for the first time" }
        status = AssignmentStatus.SUBMITTED
    }

    /** SUBMITTED or GRADED → GRADED. */
    fun graded() {
        check(status != AssignmentStatus.ASSIGNED) { "Nothing to grade" }
        status = AssignmentStatus.GRADED
    }

    /** Due date passed and nothing submitted yet. */
    fun isOverdue(now: Instant): Boolean = status == AssignmentStatus.ASSIGNED && dueAt?.isBefore(now) == true
}
