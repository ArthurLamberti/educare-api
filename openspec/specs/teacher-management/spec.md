# teacher-management Specification

## Purpose
Lets a school's admins manage the teacher accounts of their own school: create them with an initial password, list, edit and deactivate them.

## Requirements

### Requirement: Create teacher
The system SHALL let an `ADMIN` create a teacher of their tenant through `POST /api/teachers` with name (required, max 150), email (required, valid, unique within the tenant) and an initial password (min 8 chars). The new teacher SHALL have `mustChangePassword` set.

#### Scenario: Valid teacher
- **WHEN** an admin posts valid teacher data
- **THEN** the system responds 201 and the teacher can log in and is asked to change the password

#### Scenario: Duplicate email in the school
- **WHEN** the email already belongs to any user of the same tenant
- **THEN** the system responds 409

#### Scenario: Non-admin
- **WHEN** a `TEACHER` posts to `/api/teachers`
- **THEN** the system responds 403

### Requirement: List and read teachers
The system SHALL let an `ADMIN` list the teachers of their tenant (paginated, sorted by name, including active and inactive with an `active` flag) and read one by id.

#### Scenario: Listing
- **WHEN** an admin requests `GET /api/teachers`
- **THEN** the system responds 200 with only the teachers of that tenant

### Requirement: Update teacher
The system SHALL let an `ADMIN` update a teacher's name and email with the same validation and uniqueness rules as creation.

#### Scenario: Valid update
- **WHEN** an admin sends valid data to `PUT /api/teachers/{id}`
- **THEN** the system responds 200 with the updated teacher

### Requirement: Deactivate and reactivate teacher
The system SHALL let an `ADMIN` deactivate a teacher (`POST /api/teachers/{id}/deactivate`) and reactivate them (`POST /api/teachers/{id}/activate`). Teachers SHALL never be hard-deleted, so their lessons, tasks and grades remain attributable.

#### Scenario: Deactivate
- **WHEN** an admin deactivates a teacher
- **THEN** the system responds 204, the teacher can no longer log in, and existing tokens stop working

#### Scenario: Reactivate
- **WHEN** an admin reactivates a deactivated teacher
- **THEN** the teacher can log in again with their existing password
