package br.com.educare.api.aluno

import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlunoService(private val repository: AlunoRepository) {

    fun listar(pageable: Pageable): Page<AlunoResponse> =
        repository.findAll(pageable).map { it.toResponse() }

    fun buscar(id: Long): AlunoResponse = encontrar(id).toResponse()

    @Transactional
    fun criar(request: AlunoRequest): AlunoResponse {
        if (repository.existsByEmail(request.email)) {
            throw ConflictException("Já existe um aluno com o e-mail ${request.email}")
        }
        val aluno = Aluno(
            nome = request.nome,
            email = request.email,
            dataNascimento = request.dataNascimento,
        )
        return repository.save(aluno).toResponse()
    }

    @Transactional
    fun atualizar(id: Long, request: AlunoRequest): AlunoResponse {
        val aluno = encontrar(id)
        if (repository.existsByEmailAndIdNot(request.email, id)) {
            throw ConflictException("Já existe um aluno com o e-mail ${request.email}")
        }
        aluno.nome = request.nome
        aluno.email = request.email
        aluno.dataNascimento = request.dataNascimento
        return aluno.toResponse()
    }

    @Transactional
    fun remover(id: Long) {
        repository.delete(encontrar(id))
    }

    private fun encontrar(id: Long): Aluno =
        repository.findByIdOrNull(id) ?: throw NotFoundException("Aluno $id não encontrado")
}
