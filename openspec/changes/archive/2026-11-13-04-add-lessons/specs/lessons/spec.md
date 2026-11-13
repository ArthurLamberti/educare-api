# Spec Delta

## Purpose

Lets teachers schedule and manage 1:1 lessons with their students, and lets students see their own lessons, without double-booking either party.

## ADDED Requirements

### Requirement: Schedule a lesson
The system SHALL let a `TEACHER` create a lesson through `POST /api/lessons` with `studentId` (one of their active students), `startsAt` (ISO-8601 instant in the future), `durationMinutes` (15-240) and `topic` (required, max 200), plus optional `notes` (max 2000). The lesson SHALL start with status `SCHEDULED`.

#### Scenario: Valid lesson
- **WHEN** a teacher posts a valid lesson for one of their students
- **THEN** the system responds 201 with the lesson, including `endsAt` = `startsAt` + duration, and a `Location` header

#### Scenario: Another teacher's student
- **WHEN** a teacher posts a lesson for a student they do not own
- **THEN** the system responds 400 with an `errors.studentId` entry

#### Scenario: Time in the past or invalid duration
- **WHEN** `startsAt` is in the past or `durationMinutes` is outside 15-240
- **THEN** the system responds 400 with an `errors` entry for each invalid field

### Requirement: No overlapping lessons
The system SHALL reject creating or rescheduling a lesson whose time range overlaps a `SCHEDULED` lesson of the same teacher or of the same student, responding 409 with a ProblemDetail. Canceled lessons SHALL not block the time. Back-to-back lessons (one ends exactly when the next starts) SHALL be allowed.

#### Scenario: Teacher double-booked
- **WHEN** a teacher schedules 10:00-11:00 while they already have 10:30-11:30 scheduled with another student
- **THEN** the system responds 409

#### Scenario: Back-to-back
- **WHEN** a teacher schedules 11:00-12:00 right after a 10:00-11:00 lesson
- **THEN** the system creates it

### Requirement: Update and reschedule a lesson
The system SHALL let the lesson's teacher change `startsAt`, `durationMinutes`, `topic` and `notes` of a `SCHEDULED` lesson through `PUT /api/lessons/{id}`, applying the same validation and overlap rules. The student of a lesson SHALL not be changed. Canceled lessons SHALL not be editable.

#### Scenario: Teacher changes the time
- **WHEN** the teacher sends a new future `startsAt` that does not overlap
- **THEN** the system responds 200 with the new time

#### Scenario: Editing a canceled lesson
- **WHEN** the teacher updates a canceled lesson
- **THEN** the system responds 409

### Requirement: Cancel a lesson
The system SHALL let the lesson's teacher cancel a `SCHEDULED` lesson through `POST /api/lessons/{id}/cancel`, setting status `CANCELED` and keeping the record.

#### Scenario: Cancel
- **WHEN** the teacher cancels a scheduled lesson
- **THEN** the system responds 200 with status `CANCELED` and the time becomes free for new lessons

### Requirement: List and read lessons by role
The system SHALL list lessons through `GET /api/lessons`, paginated and ordered by `startsAt` ascending, filtered by optional `from`/`to` instants (`startsAt >= from`, `startsAt < to`), `status`, `studentId` (teachers and admins) and `teacherId` (admins), and sortable with `sort=startsAt,desc`. A `TEACHER` SHALL see only their lessons, a `STUDENT` only their own lessons, and an `ADMIN` all lessons of the tenant. Each lesson SHALL include teacher and student id and name. Reading a lesson the caller may not see SHALL respond 404.

#### Scenario: Student sees own lessons
- **WHEN** a student requests `GET /api/lessons?from=<now>`
- **THEN** the response contains only their upcoming lessons, in chronological order

#### Scenario: Student cannot modify
- **WHEN** a student sends `PUT /api/lessons/{id}` for their own lesson
- **THEN** the system responds 403

#### Scenario: Another teacher's lesson
- **WHEN** a teacher requests `GET /api/lessons/{id}` of another teacher's lesson
- **THEN** the system responds 404
