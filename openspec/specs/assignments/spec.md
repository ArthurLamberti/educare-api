# assignments Specification

## Purpose
Lets teachers keep a library of tasks and assign them to their students with optional due dates, and lets students see the work assigned to them.

## Requirements

### Requirement: Task library
The system SHALL let a `TEACHER` create (`POST /api/tasks`), list (`GET /api/tasks`, paginated, newest first, optional `q` title search), read, and update tasks with `title` (required, max 200) and `instructions` (required, max 10000). Teachers SHALL only see their own tasks; other teachers' tasks SHALL respond 404.

#### Scenario: Create task
- **WHEN** a teacher posts a valid title and instructions
- **THEN** the system responds 201 with the task

#### Scenario: Invalid task
- **WHEN** the title is blank
- **THEN** the system responds 400 with an `errors.title` entry

#### Scenario: Another teacher's task
- **WHEN** a teacher reads a task created by another teacher
- **THEN** the system responds 404

### Requirement: Delete and archive tasks
The system SHALL delete a task without assignments (`DELETE /api/tasks/{id}`, 204). A task with assignments SHALL not be deleted (409); instead the teacher SHALL be able to archive it (`POST /api/tasks/{id}/archive`, and `/unarchive`), which hides it from the default list (`includeArchived=true` shows it) and prevents new assignments.

#### Scenario: Delete task in use
- **WHEN** a teacher deletes a task that has an assignment
- **THEN** the system responds 409 and the task remains

#### Scenario: Assign archived task
- **WHEN** a teacher assigns an archived task
- **THEN** the system responds 409

### Requirement: Assign a task
The system SHALL let the task's teacher assign it through `POST /api/tasks/{id}/assignments` with `studentIds` (1 or more of their active students) and optional `dueAt` (future instant), creating one `ASSIGNED` assignment per student. Assigning to a student who already has an unfinished assignment of the same task SHALL be rejected with 409 for the whole request.

#### Scenario: Assign to two students
- **WHEN** a teacher assigns a task to two of their students with a due date
- **THEN** the system responds 201 with two assignments in status `ASSIGNED`

#### Scenario: Student not owned
- **WHEN** `studentIds` contains a student of another teacher
- **THEN** the system responds 400 with an `errors.studentIds` entry and creates nothing

#### Scenario: Already assigned
- **WHEN** one of the students already has the same task in status `ASSIGNED`
- **THEN** the system responds 409 and creates nothing

### Requirement: Unassign
The system SHALL let the teacher delete an assignment in status `ASSIGNED` (`DELETE /api/assignments/{id}`, 204). Assignments with a submission SHALL not be deleted (409).

#### Scenario: Unassign untouched work
- **WHEN** the teacher deletes an `ASSIGNED` assignment
- **THEN** the system responds 204 and the student no longer sees it

### Requirement: List and read assignments by role
The system SHALL list assignments through `GET /api/assignments`, paginated, filtered by optional `status`, `studentId` (teacher/admin) and `taskId`, ordered by due date (no due date last) then assignment date. A `STUDENT` SHALL see only their assignments, a `TEACHER` only assignments of their tasks, an `ADMIN` all of the tenant. Each item SHALL include task title and instructions, student id and name, `dueAt`, `assignedAt`, `status` and `overdue` (due date passed and not yet submitted).

#### Scenario: Student's to-do list
- **WHEN** a student requests `GET /api/assignments?status=ASSIGNED`
- **THEN** the response contains only their pending assignments, soonest due first

#### Scenario: Overdue flag
- **WHEN** an `ASSIGNED` assignment's `dueAt` is in the past
- **THEN** it is returned with `overdue: true`

#### Scenario: Student writes
- **WHEN** a student posts to `/api/tasks`
- **THEN** the system responds 403
