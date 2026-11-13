# Tasks

## 1. Data model

- [ ] 1.1 Add migration `V3__student_accounts.sql` recreating `students` as a tenant-owned profile (`user_id` unique FK, `teacher_id` FK, `birth_date`) and verify startup validation passes
- [ ] 1.2 Update `Student` entity (tenant-owned, `@OneToOne user`, `@ManyToOne teacher`) and DTOs (`initialPassword` and `teacherId` on request; `teacherId`, `teacherName`, `active` on response); verify the project compiles

## 2. Service rules

- [ ] 2.1 Implement create (user + profile in one transaction, per-tenant email uniqueness across users, admin must pass an active `teacherId`) and verify tests for valid data, invalid data, duplicate email and admin-without-teacher
- [ ] 2.2 Implement role-scoped list/get/update (teacher sees own, admin sees all and can filter/reassign, student gets 403) and verify tests for each scenario including another teacher's student → 404
- [ ] 2.3 Implement deactivate (`DELETE`) and `POST /{id}/activate`, hide inactive from default lists, support `includeInactive=true`; verify tests that a deactivated student cannot log in and reappears after activation

## 3. Integration

- [ ] 3.1 Verify end to end with MockMvc: teacher creates student → student logs in with `mustChangePassword = true` → changes password → `GET /api/auth/me` shows the flag cleared
- [ ] 3.2 Update `README.md` API section for the new student request/response shape and verify the curl examples run
