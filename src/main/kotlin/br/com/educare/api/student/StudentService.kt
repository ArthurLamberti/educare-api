package br.com.educare.api.student

import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StudentService(private val repository: StudentRepository) {

    fun list(pageable: Pageable): Page<StudentResponse> =
        repository.findAll(pageable).map { it.toResponse() }

    fun get(id: Long): StudentResponse = find(id).toResponse()

    @Transactional
    fun create(request: StudentRequest): StudentResponse {
        if (repository.existsByEmail(request.email)) {
            throw ConflictException("A student with email ${request.email} already exists")
        }
        val student = Student(
            name = request.name,
            email = request.email,
            birthDate = request.birthDate,
        )
        return repository.save(student).toResponse()
    }

    @Transactional
    fun update(id: Long, request: StudentRequest): StudentResponse {
        val student = find(id)
        if (repository.existsByEmailAndIdNot(request.email, id)) {
            throw ConflictException("A student with email ${request.email} already exists")
        }
        student.name = request.name
        student.email = request.email
        student.birthDate = request.birthDate
        return student.toResponse()
    }

    @Transactional
    fun delete(id: Long) {
        repository.delete(find(id))
    }

    private fun find(id: Long): Student =
        repository.findByIdOrNull(id) ?: throw NotFoundException("Student $id not found")
}
