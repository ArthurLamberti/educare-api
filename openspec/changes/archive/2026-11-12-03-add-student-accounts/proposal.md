# Proposal

## Why

Students must log in to see their lessons, answer assignments and review flashcards, and each teacher must manage only their own students. Today a student is just a record with no account, no school and no teacher.

## What Changes

- A student is now a user account with role `STUDENT` (name, email, password) plus a student profile (birth date, owning teacher), inside one tenant.
- Teachers create their students with an initial password; the student must change it on first login (reusing the change-password flow from change 02).
- Teachers see and manage only their own students; admins see every student of the school and can reassign a student to another teacher.
- **BREAKING**: `DELETE /api/students/{id}` deactivates the student instead of removing it, so lessons, submissions and reviews remain. Deactivated students are hidden from lists unless requested and cannot log in. A reactivate endpoint is added.
- **BREAKING**: create requires `initialPassword`; responses add `teacherId`, `teacherName` and `active`.
- Email uniqueness becomes per tenant and across all users (a student cannot reuse a teacher's email in the same school).

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `student-management`: students become tenant-scoped user accounts owned by a teacher; list/get/update are scoped by role; create takes an initial password; delete becomes deactivation; admin can reassign teacher.

## Impact

- `student` package: `Student` references `User` (1:1) and a teacher `User`; service enforces ownership.
- Migration `V3`: recreates `students` as a profile table (`user_id`, `teacher_id`, `birth_date`, tenant-owned). No production data exists to migrate.
- educare-web change `03-add-student-accounts` updates the student pages.
