package br.com.educare.api.tenant

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** `SCHOOL`: provisioned by the operator, with admins and teachers. `INDIVIDUAL`: a private teacher's own space. */
enum class TenantKind { SCHOOL, INDIVIDUAL }

/**
 * Whether the space may be used. `PENDING`: a new private space waiting to be activated (by the
 * operator; later by a confirmed payment). `SUSPENDED`: turned off after being active. Users of a
 * space that is not `ACTIVE` can log in but use nothing else (TenantFilter).
 */
enum class TenantStatus { PENDING, ACTIVE, SUSPENDED }

/** A school or a private teacher's space. Not tenant-owned itself. */
@Entity
@Table(name = "tenants")
class Tenant(
    @Column(nullable = false, length = 40, updatable = false)
    val slug: String,

    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = false, length = 64)
    var timeZone: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val kind: TenantKind = TenantKind.SCHOOL,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TenantStatus = TenantStatus.ACTIVE,

    @Column(nullable = false)
    var statusChangedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
