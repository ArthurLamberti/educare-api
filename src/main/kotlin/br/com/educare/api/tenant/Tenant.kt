package br.com.educare.api.tenant

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A school. Not tenant-owned itself. */
@Entity
@Table(name = "tenants")
class Tenant(
    @Column(nullable = false, length = 40, updatable = false)
    val slug: String,

    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = false, length = 64)
    var timeZone: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
