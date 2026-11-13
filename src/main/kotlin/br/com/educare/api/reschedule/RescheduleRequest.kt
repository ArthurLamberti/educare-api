package br.com.educare.api.reschedule

import br.com.educare.api.lesson.Lesson
import br.com.educare.api.tenant.TenantOwnedEntity
import br.com.educare.api.user.User
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
import java.time.Instant

enum class RescheduleStatus { PENDING, APPROVED, REJECTED, WITHDRAWN, CANCELED }

/** A student's request to move a lesson; the lesson's teacher decides. */
@Entity
@Table(name = "reschedule_requests")
class RescheduleRequest(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false, updatable = false)
    val lesson: Lesson,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    val requestedBy: User,

    /** The lesson's start when the request was made (history). */
    @Column(nullable = false, updatable = false)
    val originalStartsAt: Instant,

    @Column(nullable = false, updatable = false)
    val proposedStartsAt: Instant,

    @Column(length = 500, updatable = false)
    val reason: String?,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : TenantOwnedEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RescheduleStatus = RescheduleStatus.PENDING
        protected set

    @Column(length = 500)
    var decisionNote: String? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    var decidedBy: User? = null
        protected set

    var decidedAt: Instant? = null
        protected set

    fun approve(by: User, at: Instant) = decide(RescheduleStatus.APPROVED, by, at, null)

    fun reject(by: User, at: Instant, note: String?) = decide(RescheduleStatus.REJECTED, by, at, note)

    /** Closed because the lesson was canceled or moved directly by the teacher. */
    fun close(at: Instant) {
        status = RescheduleStatus.CANCELED
        decidedAt = at
    }

    fun withdraw(at: Instant) {
        status = RescheduleStatus.WITHDRAWN
        decidedAt = at
    }

    private fun decide(status: RescheduleStatus, by: User, at: Instant, note: String?) {
        this.status = status
        decidedBy = by
        decidedAt = at
        decisionNote = note
    }
}
