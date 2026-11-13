# Proposal

## Why

Teachers give homework between lessons. They need a library of tasks they write once and assign to one or more of their students, each with an optional due date, and students need to see what they have to do.

## What Changes

- **Tasks**: a teacher's reusable exercise (title, instructions). Teachers create, list, read, update and delete their tasks.
- **Assignments**: a task given to one student, with an optional due date and status (`ASSIGNED`, later `SUBMITTED`/`GRADED` in change 07). A teacher can assign one task to several of their students at once, and unassign while nothing has been submitted.
- Students list and read their assignments, including the task's title and instructions.
- A task that has assignments cannot be deleted (archive it instead); edits to a task are visible in its assignments only while they are not yet submitted (instructions are snapshotted at submission in change 07).

## Capabilities

### New Capabilities
- `assignments`: teacher task library and assignment of tasks to students.

### Modified Capabilities
None.

## Impact

- New `task` and `assignment` packages; migration `V6__create_tasks_and_assignments.sql`.
- educare-web change `06-add-assignments` adds the task library and "my assignments" pages.
