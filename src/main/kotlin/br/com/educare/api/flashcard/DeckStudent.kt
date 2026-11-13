package br.com.educare.api.flashcard

import br.com.educare.api.student.Student
import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Embeddable
data class DeckStudentId(
    @Column(name = "deck_id") val deckId: Long = 0,
    @Column(name = "student_id") val studentId: Long = 0,
) : Serializable

/** A deck proposed to a student. */
@Entity
@Table(name = "deck_students")
class DeckStudent(
    @MapsId("deckId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id")
    val deck: Deck,

    @MapsId("studentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    val student: Student,

    @Column(nullable = false, updatable = false)
    val assignedAt: Instant,
) : TenantOwnedEntity() {

    @EmbeddedId
    val id: DeckStudentId = DeckStudentId(requireNotNull(deck.id), requireNotNull(student.id))
}
