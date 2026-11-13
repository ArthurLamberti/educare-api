# Spec Delta

## Purpose

Lets students answer their assignments, lets teachers grade answers with a 0-10 score and feedback, and lets students see the result.

## ADDED Requirements

### Requirement: Submit an answer
The system SHALL let the assigned `STUDENT` submit or replace their answer through `PUT /api/assignments/{id}/submission` with `answer` (required, max 20000 chars) while the assignment is `ASSIGNED` or `SUBMITTED`. The first submission SHALL set the status to `SUBMITTED`, record `submittedAt`, and snapshot the task instructions. Each later edit SHALL update the answer and `updatedAt`.

#### Scenario: First submission
- **WHEN** a student submits an answer to an `ASSIGNED` assignment
- **THEN** the system responds 200, the status becomes `SUBMITTED` and `submittedAt` is set

#### Scenario: Edit before grading
- **WHEN** the student submits again while it is `SUBMITTED`
- **THEN** the answer is replaced and `submittedAt` keeps the first submission time

#### Scenario: Edit after grading
- **WHEN** the student submits to a `GRADED` assignment
- **THEN** the system responds 409

#### Scenario: Someone else's assignment
- **WHEN** a student submits to another student's assignment
- **THEN** the system responds 404

### Requirement: Late submissions
The system SHALL accept submissions after `dueAt` and mark them with `late: true`.

#### Scenario: After due date
- **WHEN** a student submits after the due date
- **THEN** the submission is accepted with `late: true`

### Requirement: Grade a submission
The system SHALL let the teacher of the assignment's task grade a `SUBMITTED` or `GRADED` assignment through `PUT /api/assignments/{id}/grade` with `score` (0 to 10, at most one decimal) and `feedback` (required, max 10000). Grading SHALL set status `GRADED`, record `gradedAt`, and allow later revisions that overwrite score and feedback.

#### Scenario: Grade
- **WHEN** the teacher grades a submitted assignment with score 8.5 and feedback
- **THEN** the system responds 200 and the status becomes `GRADED`

#### Scenario: Grade before submission
- **WHEN** the teacher grades an `ASSIGNED` assignment
- **THEN** the system responds 409

#### Scenario: Invalid score
- **WHEN** the score is 10.5, negative, or has two decimals (7.25)
- **THEN** the system responds 400 with an `errors.score` entry

### Requirement: Feedback visibility
The system SHALL include in assignment responses the submission (answer, submittedAt, updatedAt, late, instructions snapshot) once submitted, and the grade (score, feedback, gradedAt) once graded. The student SHALL see the grade only when the status is `GRADED`.

#### Scenario: Student reads graded work
- **WHEN** a student reads their graded assignment
- **THEN** the response contains their answer, score and feedback

### Requirement: Grading queue
The system SHALL let a teacher list assignments awaiting grading with `GET /api/assignments?status=SUBMITTED`, oldest submission first.

#### Scenario: Queue order
- **WHEN** two students submitted at different times
- **THEN** the earlier submission is listed first
