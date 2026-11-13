# Spec Delta

## MODIFIED Requirements

### Requirement: List students
The system SHALL return the active students of the current tenant as a paginated list, 20 per page by default, sorted by name. A `TEACHER` SHALL only see their own students; an `ADMIN` SHALL see all students of the tenant and MAY filter by `teacherId`. Passing `includeInactive=true` SHALL also return deactivated students. A `STUDENT` SHALL receive 403.

#### Scenario: Default listing
- **WHEN** a teacher requests `GET /api/students` without paging parameters
- **THEN** the system responds 200 with the first page of up to 20 of that teacher's active students sorted by name, including total elements and total pages

#### Scenario: Admin sees the whole school
- **WHEN** an admin requests `GET /api/students`
- **THEN** the response contains the active students of every teacher in the tenant, each with `teacherId` and `teacherName`

#### Scenario: Student is forbidden
- **WHEN** a student requests `GET /api/students`
- **THEN** the system responds 403

### Requirement: Get a student
The system SHALL return a single student by id, and respond 404 with a ProblemDetail when it does not exist, belongs to another tenant, or (for a `TEACHER`) belongs to another teacher.

#### Scenario: Existing student
- **WHEN** the student's teacher requests `GET /api/students/{id}`
- **THEN** the system responds 200 with id, name, email, birthDate, teacherId, teacherName, active and createdAt

#### Scenario: Missing student
- **WHEN** a client requests `GET /api/students/{id}` for an id that does not exist
- **THEN** the system responds 404 with a ProblemDetail

#### Scenario: Another teacher's student
- **WHEN** a teacher requests a student owned by a different teacher
- **THEN** the system responds 404

### Requirement: Create a student
The system SHALL let a `TEACHER` create a student account from a name (required, max 150), an email (required, valid, max 255, unique among all users of the tenant), an optional past birth date and an initial password (required, min 8). The student SHALL be owned by the creating teacher, have role `STUDENT`, and have `mustChangePassword` set. An `ADMIN` SHALL also be able to create a student by giving a `teacherId` of an active teacher.

#### Scenario: Valid data
- **WHEN** a teacher posts valid data to `POST /api/students`
- **THEN** the system responds 201 with the created student and a `Location` header, and the student can log in and is asked to change the password

#### Scenario: Invalid data
- **WHEN** a client posts a blank name, a malformed email or an initial password shorter than 8 chars
- **THEN** the system responds 400 with a ProblemDetail whose `errors` map has an entry per invalid field

#### Scenario: Duplicate email
- **WHEN** a client posts an email already used by any user of the same tenant
- **THEN** the system responds 409 with a ProblemDetail

#### Scenario: Admin without teacher
- **WHEN** an admin posts a student without `teacherId`
- **THEN** the system responds 400 with an `errors.teacherId` entry

### Requirement: Update a student
The system SHALL replace a student's name, email and birth date with the same validation and email uniqueness rules as creation. Only the owning teacher or an admin SHALL update; an admin MAY also change `teacherId` to another active teacher of the tenant.

#### Scenario: Valid update
- **WHEN** the owning teacher sends valid data to `PUT /api/students/{id}`
- **THEN** the system responds 200 with the updated student

#### Scenario: Email taken by another student
- **WHEN** a client updates a student with an email used by a different user of the tenant
- **THEN** the system responds 409 with a ProblemDetail

#### Scenario: Admin reassigns teacher
- **WHEN** an admin updates a student with a different active `teacherId`
- **THEN** the student is now listed for the new teacher and no longer for the old one

### Requirement: Delete a student
The system SHALL deactivate a student on `DELETE /api/students/{id}` and respond 204, or 404 when it does not exist or is not visible to the caller. A deactivated student SHALL not log in, SHALL be hidden from default lists, and SHALL keep all their history. `POST /api/students/{id}/activate` SHALL reactivate them.

#### Scenario: Existing student
- **WHEN** the owning teacher sends `DELETE /api/students/{id}`
- **THEN** the system responds 204, the student disappears from the default list, and their login is rejected with 401

#### Scenario: Reactivation
- **WHEN** the owning teacher or an admin sends `POST /api/students/{id}/activate` for a deactivated student
- **THEN** the system responds 204 and the student can log in again
