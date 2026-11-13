package br.com.educare.api.user

import br.com.educare.api.tenant.TenantOwnedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class Role { ADMIN, TEACHER, STUDENT }

/** Anyone who can log in to a school. Email is unique per school and stored lowercased. */
@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 150)
    var name: String,

    email: String,

    @Column(nullable = false, length = 100)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val role: Role,

    @Column(nullable = false)
    var mustChangePassword: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,

    /** Null only for a self sign-up that has not confirmed the email yet; such users cannot log in. */
    var emailConfirmedAt: Instant? = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : TenantOwnedEntity() {

    @Column(nullable = false, length = 255)
    var email: String = normalizeEmail(email)
        set(value) {
            field = normalizeEmail(value)
        }

    companion object {
        fun normalizeEmail(email: String) = email.trim().lowercase()
    }
}
