package br.com.educare.api.signup

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * A confirmation link sent by email. Not tenant-filtered: it is looked up by its token before
 * anyone is logged in, so the sign-up code runs as root.
 */
@Entity
@Table(name = "email_confirmations")
class EmailConfirmation(
    @Column(nullable = false, updatable = false)
    val tenantId: Long,

    @Column(nullable = false, updatable = false)
    val userId: Long,

    /** SHA-256 (hex) of the token; the token itself is only in the email. */
    @Column(nullable = false, length = 64, updatable = false)
    val tokenHash: String,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(nullable = false, updatable = false)
    val expiresAt: Instant,

    var usedAt: Instant? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

interface EmailConfirmationRepository : JpaRepository<EmailConfirmation, Long> {
    fun findByTokenHash(tokenHash: String): EmailConfirmation?

    /** Older links stop working when a new one is sent. */
    @Modifying
    @Query("update EmailConfirmation c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
    fun invalidateAll(userId: Long, now: Instant): Int
}
