# student-management Specification

## Purpose

Lets the API register and maintain students: list, read, create, update and delete student records with validated data and unique emails.

## Requirements

### Requirement: List students
The system SHALL return students as a paginated list, 20 per page by default, sorted by name.

#### Scenario: Default listing
- **WHEN** a client requests `GET /api/students` without paging parameters
- **THEN** the system responds 200 with the first page of up to 20 students sorted by name, including total elements and total pages

### Requirement: Get a student
The system SHALL return a single student by id, and respond 404 with a ProblemDetail when it does not exist.

#### Scenario: Existing student
- **WHEN** a client requests `GET /api/students/{id}` for an existing student
- **THEN** the system responds 200 with id, name, email, birthDate and createdAt

#### Scenario: Missing student
- **WHEN** a client requests `GET /api/students/{id}` for an id that does not exist
- **THEN** the system responds 404 with a ProblemDetail

### Requirement: Create a student
The system SHALL create a student from a name (required, max 150), an email (required, valid, max 255, unique) and an optional past birth date.

#### Scenario: Valid data
- **WHEN** a client posts valid data to `POST /api/students`
- **THEN** the system responds 201 with the created student and a `Location` header pointing to it

#### Scenario: Invalid data
- **WHEN** a client posts a blank name or a malformed email
- **THEN** the system responds 400 with a ProblemDetail whose `errors` map has an entry per invalid field

#### Scenario: Duplicate email
- **WHEN** a client posts an email already used by another student
- **THEN** the system responds 409 with a ProblemDetail

### Requirement: Update a student
The system SHALL replace a student's name, email and birth date with the same validation and email uniqueness rules as creation.

#### Scenario: Valid update
- **WHEN** a client sends valid data to `PUT /api/students/{id}` for an existing student
- **THEN** the system responds 200 with the updated student

#### Scenario: Email taken by another student
- **WHEN** a client updates a student with an email used by a different student
- **THEN** the system responds 409 with a ProblemDetail

### Requirement: Delete a student
The system SHALL delete a student by id and respond 204, or 404 when it does not exist.

#### Scenario: Existing student
- **WHEN** a client sends `DELETE /api/students/{id}` for an existing student
- **THEN** the system responds 204 and a later `GET` for that id responds 404
