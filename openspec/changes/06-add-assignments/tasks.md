# Tasks

## 1. Data model

- [ ] 1.1 Add migration `V6__create_tasks_and_assignments.sql` (tables, status check, partial unique index) and verify it applies
- [ ] 1.2 Create `Task` and `Assignment` entities (tenant-owned), repositories and DTOs; verify the project compiles

## 2. Task library

- [ ] 2.1 Implement task CRUD with ownership, title search, archive/unarchive and delete-in-use 409; verify tests for create, invalid task, another teacher's task (404), delete in use and listing with/without archived

## 3. Assigning

- [ ] 3.1 Implement `POST /api/tasks/{id}/assignments` (bulk, all-or-nothing) and `DELETE /api/assignments/{id}`; verify tests for two students, student not owned, already assigned, archived task and unassigning
- [ ] 3.2 Implement role-scoped `GET /api/assignments` and `GET /api/assignments/{id}` with filters, ordering and `overdue`; verify tests for student to-do list, overdue flag with a fixed clock, teacher scope, and student 403 on writes

## 4. Documentation

- [ ] 4.1 Document tasks and assignments endpoints in `README.md` and verify the curl examples run
