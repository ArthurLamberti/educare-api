package br.com.educare.api.teacher

import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import br.com.educare.api.user.UserStatusCache
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Teachers of the current school (tenant scoping is automatic). Teachers are never deleted. */
@Service
@Transactional(readOnly = true)
class TeacherService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userStatus: UserStatusCache,
) {

    fun list(pageable: Pageable): Page<TeacherResponse> =
        users.findAllByRole(Role.TEACHER, pageable).map { it.toTeacherResponse() }

    fun get(id: Long): TeacherResponse = find(id).toTeacherResponse()

    @Transactional
    fun create(request: CreateTeacherRequest): TeacherResponse {
        val email = User.normalizeEmail(request.email)
        if (users.existsByEmail(email)) throw emailTaken(email)
        val teacher = User(
            name = request.name,
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(request.initialPassword)),
            role = Role.TEACHER,
            mustChangePassword = true,
        )
        return users.save(teacher).toTeacherResponse()
    }

    @Transactional
    fun update(id: Long, request: UpdateTeacherRequest): TeacherResponse {
        val teacher = find(id)
        val email = User.normalizeEmail(request.email)
        if (users.existsByEmailAndIdNot(email, id)) throw emailTaken(email)
        teacher.name = request.name
        teacher.email = email
        return teacher.toTeacherResponse()
    }

    @Transactional
    fun setActive(id: Long, active: Boolean) {
        find(id).active = active
        userStatus.evict(id)
    }

    private fun find(id: Long): User =
        users.findByIdAndRole(id, Role.TEACHER) ?: throw NotFoundException("Teacher $id not found")

    private fun emailTaken(email: String) = ConflictException("A user with email $email already exists in this school")
}
