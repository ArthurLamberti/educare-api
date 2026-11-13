# Tasks

## 1. Data model

- [ ] 1.1 Add migration `V4__create_lessons.sql` (table, checks, `btree_gist`, exclusion constraints for teacher and student) and verify it applies and Hibernate validation passes
- [ ] 1.2 Create `Lesson` entity (tenant-owned, keeps `endsAt` in sync), repository, DTOs with `endsAt` and teacher/student names; verify the project compiles

## 2. Teacher operations

- [ ] 2.1 Implement `POST /api/lessons` with validation, ownership of the student and overlap checks (constraint violations mapped to 409); verify tests for valid lesson, another teacher's student, past time, invalid duration, teacher overlap, student overlap and back-to-back
- [ ] 2.2 Implement `PUT /api/lessons/{id}` through a reusable `reschedule` path and `POST /api/lessons/{id}/cancel`; verify tests for time change, overlap on update, editing a canceled lesson (409) and that canceling frees the slot

## 3. Reading by role

- [ ] 3.1 Implement `GET /api/lessons` (filters `from`, `to`, `status`, `studentId`; ordered by `startsAt`) and `GET /api/lessons/{id}` scoped by role; verify tests for teacher, student and admin visibility, student 403 on writes, and 404 for another teacher's lesson

## 4. Documentation

- [ ] 4.1 Document the lesson endpoints and the UTC/time-zone convention in `README.md` and verify the curl examples run
