# assignment-submissions Specification

## Purpose
Lets students answer their assignments, lets teachers grade answers with a 0-10 score and feedback, and lets students see the result.

## Requirements

### Requirement: Submit an answer
The system SHALL let the assigned `STUDENT` submit or replace their answer through `PUT /api/assignments/{id}/submission` with `answer` (required, max 20000 chars) while the assignment is `ASSIGNED` or `SUBMITTED`, that is, until the teacher publishes a grade. The first submission SHALL set the status to `SUBMITTED`, record `submittedAt`, and snapshot the task instructions. Each later submission SHALL replace the answer and update `updatedAt`. Every submission SHALL clear the student's answer draft.

#### Scenario: First submission
- **WHEN** a student submits an answer to an `ASSIGNED` assignment
- **THEN** the system responds 200, the status becomes `SUBMITTED` and `submittedAt` is set

#### Scenario: Edit before grading
- **WHEN** the student submits again while it is `SUBMITTED`
- **THEN** the answer is replaced and `submittedAt` keeps the first submission time

#### Scenario: Edit while the teacher has a grade draft
- **WHEN** the teacher saved a grade draft but did not publish it and the student submits again
- **THEN** the answer is replaced and the submission `version` changes

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

### Requirement: Answer draft
The assigned `STUDENT` SHALL be able to save a private draft of their answer through `PUT /api/assignments/{id}/draft` with `answer` (may be blank, max 20000 chars) while the assignment is `ASSIGNED` or `SUBMITTED`; with `GRADED` the system SHALL respond 409. Saving a draft SHALL NOT change the status, the submitted answer or `updatedAt`. The draft and its save time SHALL be returned only to that student; the teacher and admins SHALL never see it. Submitting an answer SHALL clear the draft.

#### Scenario: Draft before the first submission
- **WHEN** a student saves a draft of an `ASSIGNED` assignment
- **THEN** the status stays `ASSIGNED` and the teacher's response has no answer and no draft

#### Scenario: Draft after submitting
- **WHEN** a student saves a draft of a `SUBMITTED` assignment
- **THEN** the teacher still sees the submitted answer and the student sees both the submitted answer and the draft

#### Scenario: Submit clears the draft
- **WHEN** the student submits after saving a draft
- **THEN** the submitted answer is the sent text and the draft is gone

### Requirement: Grade draft
The teacher of the assignment's task SHALL be able to save a private correction through `PUT /api/assignments/{id}/grade-draft` with optional `score` (same rules as grading), optional `feedback` (max 10000) and the reviewed submission `version`, while the assignment is `SUBMITTED` or `GRADED`; with `ASSIGNED` the system SHALL respond 409, and with a `version` other than the current one it SHALL respond 409. Saving SHALL NOT change the status or the published grade. The draft SHALL be returned only to that teacher, and lists SHALL flag assignments with a grade draft (`hasGradeDraft`) for them. Publishing a grade SHALL clear the draft.

#### Scenario: Correction in progress
- **WHEN** the teacher saves a grade draft of a `SUBMITTED` assignment
- **THEN** the status stays `SUBMITTED`, the student sees no score or feedback, and the teacher gets the draft back

#### Scenario: Revising a published grade
- **WHEN** the teacher saves a grade draft of a `GRADED` assignment
- **THEN** the student keeps seeing the published score and feedback until the teacher publishes the new grade

#### Scenario: Answer changed meanwhile
- **WHEN** the student resubmits after the teacher opened the answer and the teacher saves a draft with the old version
- **THEN** the system responds 409
