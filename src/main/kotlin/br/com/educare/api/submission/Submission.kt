package br.com.educare.api.submission

import br.com.educare.api.assignment.Assignment
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
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

/** The student's current answer to an assignment and, once graded, the teacher's score and feedback. */
@Entity
@Table(name = "submissions")
class Submission(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false, updatable = false, unique = true)
    val assignment: Assignment,

    answer: String,

    @Column(nullable = false, updatable = false, length = 10000)
    val instructionsSnapshot: String,

    /** First submission; later edits only change [updatedAt]. */
    @Column(nullable = false, updatable = false)
    val submittedAt: Instant,

    /** Submitted after the due date (decided at the first submission). */
    @Column(nullable = false, updatable = false)
    val late: Boolean,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 20000)
    var answer: String = answer
        protected set

    @Column(nullable = false)
    var updatedAt: Instant = submittedAt
        protected set

    @Column(precision = 3, scale = 1)
    var score: BigDecimal? = null
        protected set

    @Column(length = 10000)
    var feedback: String? = null
        protected set

    var gradedAt: Instant? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    var gradedBy: User? = null
        protected set

    /** Optimistic lock: a grade never applies to an answer the teacher did not see. */
    @Version
    var version: Long = 0
        protected set

    fun editAnswer(answer: String, at: Instant) {
        this.answer = answer
        updatedAt = at
    }

    fun grade(score: BigDecimal, feedback: String, by: User, at: Instant) {
        this.score = score
        this.feedback = feedback
        gradedBy = by
        gradedAt = at
    }
}
