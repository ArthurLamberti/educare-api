package br.com.educare.api.level

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * A step of the level scale tasks are tagged with (CEFR A1 to C2 today). Shared by every school and
 * kept as data, so the scale can change without code: new levels are added and old ones deactivated;
 * an inactive level stays on the tasks that have it but cannot be chosen again.
 */
@Entity
@Table(name = "levels")
class Level(
    @Column(nullable = false, length = 20)
    val code: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false)
    val position: Int,

    @Column(nullable = false)
    val active: Boolean = true,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)

interface LevelRepository : JpaRepository<Level, Long> {
    fun findByActiveTrueOrderByPosition(): List<Level>
}

data class LevelResponse(val id: Long, val code: String, val name: String)

fun Level.toResponse() = LevelResponse(requireNotNull(id), code, name)

@RestController
class LevelController(private val levels: LevelRepository) {

    /** The active levels in scale order, for any signed-in user; clients never hard-code the scale. */
    @GetMapping("/api/levels")
    @PreAuthorize("isAuthenticated()")
    fun list(): List<LevelResponse> = levels.findByActiveTrueOrderByPosition().map { it.toResponse() }
}
