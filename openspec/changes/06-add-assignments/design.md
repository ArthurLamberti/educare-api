# Design

## Context

Changes 02-03 provide teachers, students and ownership rules. Change 07 adds submissions and grading on top of assignments.

## Goals / Non-Goals

**Goals:** reusable tasks, bulk assignment, a status field that change 07 extends.
**Non-Goals:** file attachments, rich text beyond Markdown stored as plain text, task sharing between teachers, recurring assignments.

## Decisions

- **Two tables**: `tasks(id, tenant_id, teacher_id, title, instructions, archived, created_at, updated_at)` and `assignments(id, tenant_id, task_id, student_id, due_at, status, assigned_at)`. Separating task from assignment lets one task be reused and lets each student progress independently. Alternative: a task per student - duplicates content and loses reuse.
- **Instructions as Markdown text**; the web app renders it safely (no raw HTML).
- **Duplicate rule** enforced by a partial unique index `ON assignments(task_id, student_id) WHERE status = 'ASSIGNED'` plus a service check for a clear 409.
- **Bulk assign is all-or-nothing** in one transaction, so the teacher never ends up with a half-applied assignment.
- **`overdue`** is computed at read time with the injected `Clock` (change 05), not stored.
- **Status enum** `ASSIGNED | SUBMITTED | GRADED` created now (check constraint) so change 07 needs no schema change to the column.

## Risks / Trade-offs

- [Teacher edits instructions after students started] → Acceptable; change 07 snapshots instructions into the submission so grading refers to what the student saw.
