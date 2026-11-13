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

    /** Due date passed and nothing submitted yet. */
    fun isOverdue(now: Instant): Boolean = status == AssignmentStatus.ASSIGNED && dueAt?.isBefore(now) == true
}
