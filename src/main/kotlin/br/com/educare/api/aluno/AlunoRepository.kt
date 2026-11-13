package br.com.educare.api.aluno

import org.springframework.data.jpa.repository.JpaRepository

interface AlunoRepository : JpaRepository<Aluno, Long> {
    fun existsByEmail(email: String): Boolean
    fun existsByEmailAndIdNot(email: String, id: Long): Boolean
}
