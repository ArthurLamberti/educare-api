package br.com.educare.api.student

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "students")
class Student(
    @Column(nullable = false, length = 150)
    var name: String,

    @Column(nullable = false, unique = true)
    var email: String,

    var birthDate: LocalDate? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
