# Spec Delta

## Purpose

Lets a student ask, at least 2 days in advance, to move one of their lessons to another time, and lets the lesson's teacher approve or reject that request.

## ADDED Requirements

### Requirement: Request a new time
The system SHALL let a `STUDENT` create a reschedule request for one of their `SCHEDULED` lessons through `POST /api/lessons/{id}/reschedule-requests` with `proposedStartsAt` (future instant) and optional `reason` (max 500). The request SHALL start as `PENDING`. The duration of the lesson is kept.

#### Scenario: Valid request
- **WHEN** a student requests a new time 5 days before the lesson
- **THEN** the system responds 201 with the request in status `PENDING`

#### Scenario: Not the student's lesson
- **WHEN** a student requests a change for a lesson of another student
- **THEN** the system responds 404

### Requirement: Two-day advance rule
The system SHALL accept a request only if it is made at least 48 hours before the lesson's current `startsAt`; otherwise it SHALL respond 422 with a ProblemDetail explaining the rule.

#### Scenario: Exactly 48 hours before
- **WHEN** the request is made exactly 48 hours before the lesson starts
- **THEN** the system accepts it

#### Scenario: Less than 48 hours before
- **WHEN** the request is made 47 hours before the lesson starts
- **THEN** the system responds 422 and no request is created

### Requirement: One pending request per lesson
The system SHALL allow at most one `PENDING` request per lesson; a second one SHALL be rejected with 409 until the first is decided or withdrawn.

#### Scenario: Second pending request
- **WHEN** a student creates a request for a lesson that already has a pending one
- **THEN** the system responds 409

### Requirement: Teacher decides
The system SHALL let the lesson's teacher list requests (`GET /api/reschedule-requests?status=PENDING`), approve (`POST /api/reschedule-requests/{id}/approve`) or reject (`POST /api/reschedule-requests/{id}/reject` with optional `note`, max 500) a `PENDING` request. Approval SHALL move the lesson to `proposedStartsAt` using the same validation and overlap rules as a teacher's time change; if the proposed time is now in the past or overlaps, approval SHALL respond 409 and leave both request and lesson unchanged.

#### Scenario: Approve
- **WHEN** the teacher approves a pending request whose time is free
- **THEN** the request becomes `APPROVED` and the lesson's `startsAt` equals `proposedStartsAt`

#### Scenario: Approve into a conflict
- **WHEN** the proposed time overlaps another scheduled lesson of the teacher
- **THEN** the system responds 409 and the request stays `PENDING`

#### Scenario: Reject
- **WHEN** the teacher rejects with a note
- **THEN** the request becomes `REJECTED` with the note, and the lesson is unchanged

#### Scenario: Deciding twice
- **WHEN** the teacher approves a request that is no longer `PENDING`
- **THEN** the system responds 409

### Requirement: Withdraw and automatic closing
The system SHALL let the requesting student withdraw a `PENDING` request (`POST /api/reschedule-requests/{id}/withdraw`, status `WITHDRAWN`). When a lesson with a pending request is canceled or its time is changed directly by the teacher, the system SHALL set the request to `CANCELED`.

#### Scenario: Teacher changes time directly
- **WHEN** a lesson has a pending request and the teacher edits the lesson time
- **THEN** the pending request becomes `CANCELED`

### Requirement: Visibility
The system SHALL show each request with lesson id, topic, current and proposed times, reason, status, decision note and timestamps. A student SHALL see only their requests, a teacher only requests on their lessons, an admin all requests of the tenant.

#### Scenario: Student lists requests
- **WHEN** a student requests `GET /api/reschedule-requests`
- **THEN** the response contains only that student's requests, newest first
