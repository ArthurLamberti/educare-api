# Design

## Context

After change 02, `users` (tenant-owned, roles, passwords) exist, and `students` is still the original standalone table restricted to ADMIN/TEACHER.

## Goals / Non-Goals

**Goals:**
- One identity table (`users`) for login; role-specific data in profile tables.
- Ownership rules enforced in the service layer, answering 404 for resources the caller may not see.

**Non-Goals:**
- Student self-registration, guardians/parents, bulk import.

## Decisions

- **Profile table over columns on `users`**: `students(id, tenant_id, user_id unique, teacher_id, birth_date)` referencing `users`. Keeps `users` generic; later features (lessons, assignments) reference `students.id`. The API keeps exposing a flat student (name/email from the user, birth date and teacher from the profile). Alternative: nullable student columns on `users` - rejected as it mixes role-specific data into identity.
- **Student id = profile id**: `GET /api/students/{id}` keeps using the student id, so URLs stay stable for the web app.
- **Deactivation, not deletion**: sets `users.active = false`. Later tables reference students; hard delete would cascade history away.
- **Ownership**: `StudentService` filters by `teacher_id = currentUser.id` for teachers; admins are unrestricted within the tenant (tenant filter is automatic via `@TenantId`). Not-owned → `NotFoundException` (404).
- **Migration V3** drops and recreates `students` (no production data exists; documented in the migration header). Composite FK `(tenant_id, user_id)` is not used; tenant consistency is guaranteed by the service, which always creates the user and profile in the same transaction under the current tenant.
- **Create in one transaction**: user (role STUDENT, BCrypt password, `mustChangePassword = true`) + profile.

## Risks / Trade-offs

- [Admin reassigns a student while the old teacher has scheduled lessons] → Lessons keep their teacher; reassignment affects only future ownership. Revisit if this causes confusion.
- [Breaking response/request shape] → Only consumer is educare-web, updated in its change 03.
