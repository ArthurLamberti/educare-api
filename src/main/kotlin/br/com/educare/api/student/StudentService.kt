package br.com.educare.api.student

import br.com.educare.api.auth.CurrentUser
import br.com.educare.api.common.ConflictException
import br.com.educare.api.common.FieldValidationException
import br.com.educare.api.common.NotFoundException
import br.com.educare.api.user.Role
import br.com.educare.api.user.User
import br.com.educare.api.user.UserRepository
import br.com.educare.api.user.UserStatusCache
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Students of the current school. Teachers only see and manage their own students (others → 404);
 * admins see all and choose/reassign the teacher. Students are deactivated, never deleted.
 */
@Service
@Transactional(readOnly = true)
class StudentService(
    private val students: StudentRepository,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userStatus: UserStatusCache,
    private val currentUser: CurrentUser,
) {

    fun list(pageable: Pageable, teacherId: Long?, includeInactive: Boolean): Page<StudentResponse> {
        val scopedTeacher = if (currentUser.role == Role.TEACHER) currentUser.id else teacherId
        return students.search(scopedTeacher, includeInactive, pageable.withEntitySort()).map { it.toResponse() }
    }

    fun get(id: Long): StudentResponse = find(id).toResponse()

    @Transactional
    fun create(request: CreateStudentRequest): StudentResponse {
        val teacher = if (currentUser.role == Role.ADMIN) {
            activeTeacher(request.teacherId ?: throw FieldValidationException("teacherId", "must be chosen"))
        } else {
            users.findByIdOrNull(currentUser.id) ?: throw NotFoundException("User not found")
        }
        val email = User.normalizeEmail(request.email)
        if (users.existsByEmail(email)) throw emailTaken(email)
        val user = users.save(
            User(
                name = request.name,
                email = email,
                passwordHash = requireNotNull(passwordEncoder.encode(request.initialPassword)),
                role = Role.STUDENT,
                mustChangePassword = true,
            ),
        )
        return students.save(Student(user = user, teacher = teacher, birthDate = request.birthDate)).toResponse()
    }

    @Transactional
    fun update(id: Long, request: UpdateStudentRequest): StudentResponse {
        val student = find(id)
        val email = User.normalizeEmail(request.email)
        if (users.existsByEmailAndIdNot(email, requireNotNull(student.user.id))) throw emailTaken(email)
        student.user.name = request.name
        student.user.email = email
        student.birthDate = request.birthDate
        if (currentUser.role == Role.ADMIN && request.teacherId != null && request.teacherId != student.teacher.id) {
            student.teacher = activeTeacher(request.teacherId)
        }
        return student.toResponse()
    }

    @Transactional
    fun setActive(id: Long, active: Boolean) {
        val user = find(id).user
        user.active = active
        userStatus.evict(requireNotNull(user.id))
    }

    private fun find(id: Long): Student {
        val student = students.findByIdOrNull(id)
        if (student == null || (currentUser.role == Role.TEACHER && student.teacher.id != currentUser.id)) {
            throw NotFoundException("Student $id not found")
        }
        return student
    }

    private fun activeTeacher(id: Long): User =
        users.findByIdAndRole(id, Role.TEACHER)?.takeIf { it.active }
            ?: throw FieldValidationException("teacherId", "must be an active teacher of this school")

    private fun emailTaken(email: String) = ConflictException("A user with email $email already exists in this school")

    /** API sorts by response fields; map them to entity paths. */
    private fun Pageable.withEntitySort(): Pageable {
        val sort = Sort.by(sort.map { order -> order.withProperty(SORT_PATHS[order.property] ?: "user.name") }.toList())
        return PageRequest.of(pageNumber, pageSize, if (sort.isSorted) sort else Sort.by("user.name"))
    }

    companion object {
        private val SORT_PATHS = mapOf(
            "name" to "user.name",
            "email" to "user.email",
            "createdAt" to "user.createdAt",
            "birthDate" to "birthDate",
        )
    }
}
