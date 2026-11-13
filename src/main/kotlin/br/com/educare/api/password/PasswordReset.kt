package br.com.educare.api.password

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

/**
 * A "forgot password" link, for a school user ([userId]) or a platform admin ([platformAdminId]).
 * Not tenant-filtered: it is looked up by its token before anyone is logged in.
 */
@Entity
@Table(name = "password_resets")
class PasswordReset(
    @Column(updatable = false)
    val userId: Long? = null,

    @Column(updatable = false)
    val platformAdminId: Long? = null,

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

interface PasswordResetRepository : JpaRepository<PasswordReset, Long> {
    fun findByTokenHash(tokenHash: String): PasswordReset?

    /** Older links stop working when a new one is sent or one is used. */
    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.userId = :userId and r.usedAt is null")
    fun invalidateForUser(userId: Long, now: Instant): Int

    @Modifying
    @Query("update PasswordReset r set r.usedAt = :now where r.platformAdminId = :adminId and r.usedAt is null")
    fun invalidateForAdmin(adminId: Long, now: Instant): Int
}

data class ForgotPasswordRequest(@field:NotBlank @field:Email @field:Size(max = 255) val email: String)

data class ResetPasswordRequest(
    @field:NotBlank @field:Size(max = 100) val token: String,
    @field:NotBlank @field:Size(min = 8, max = 72) val newPassword: String,
)
