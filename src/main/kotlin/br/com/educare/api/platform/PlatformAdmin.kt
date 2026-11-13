package br.com.educare.api.platform

import br.com.educare.api.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** Someone who operates the platform (the operator panel). Not part of any tenant. */
@Entity
@Table(name = "platform_admins")
class PlatformAdmin(
    @Column(nullable = false, length = 150)
    var name: String,

    email: String,

    @Column(nullable = false, length = 100)
    var passwordHash: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Column(nullable = false, length = 255)
    var email: String = User.normalizeEmail(email)
        set(value) {
            field = User.normalizeEmail(value)
        }
}

interface PlatformAdminRepository : JpaRepository<PlatformAdmin, Long> {
    fun findByEmail(email: String): PlatformAdmin?
}

@ConfigurationProperties("educare.platform")
data class PlatformProperties(
    /** Platform admin created at startup when missing (EDUCARE_ADMIN_EMAIL). Empty: none. */
    val adminEmail: String = "",
    val adminName: String = "Administrador",
)
