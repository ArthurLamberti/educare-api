# authentication Specification

## Purpose
Identifies users by email and password within a school, issues access tokens, and enforces role-based access (ADMIN, TEACHER, STUDENT) across the API.

## Requirements

### Requirement: Login
The system SHALL authenticate an active user of the resolved tenant by email and password through `POST /api/auth/login`, returning an access token, its expiry instant and the user's id, name, email, role and `mustChangePassword` flag. Failed logins SHALL respond 401 with the same message whether the email or the password is wrong. A user with correct credentials whose email is not yet confirmed (self sign-up) SHALL get 403 with `code: EMAIL_NOT_CONFIRMED` and no token.

#### Scenario: Valid credentials
- **WHEN** an active user posts the correct email and password with their school's `X-Tenant`
- **THEN** the system responds 200 with `token`, `expiresAt` and `user`

#### Scenario: Wrong password or unknown email
- **WHEN** the password is wrong, or the email does not exist in that tenant
- **THEN** the system responds 401 with the detail "Invalid email or password"

#### Scenario: Deactivated user
- **WHEN** a deactivated user posts correct credentials
- **THEN** the system responds 401 with the detail "Invalid email or password"

#### Scenario: Correct credentials on another school
- **WHEN** a user of `alpha` logs in with `X-Tenant: beta`
- **THEN** the system responds 401

#### Scenario: Email not confirmed
- **WHEN** a private teacher who has not confirmed the email posts the correct password
- **THEN** the system responds 403 with `code: EMAIL_NOT_CONFIRMED`

#### Scenario: Wrong password before confirmation
- **WHEN** an unconfirmed user posts a wrong password
- **THEN** the system responds 401 with the detail "Invalid email or password"

### Requirement: Authenticated access
The system SHALL require a valid, unexpired access token in the `Authorization: Bearer` header on every endpoint except login and platform provisioning. Tokens SHALL expire 12 hours after issue. A token of a user who has since been deactivated SHALL be rejected.

#### Scenario: No token
- **WHEN** a request to `GET /api/students` has no `Authorization` header
- **THEN** the system responds 401

#### Scenario: Expired or tampered token
- **WHEN** the token is expired or its signature is invalid
- **THEN** the system responds 401

#### Scenario: Deactivated after login
- **WHEN** an admin deactivates a teacher and that teacher uses a token issued before
- **THEN** the system responds 401

### Requirement: Role-based authorization
The system SHALL authorize each endpoint by role and respond 403 when an authenticated user's role is not allowed. Until change 03, `/api/students` endpoints SHALL be allowed for `ADMIN` and `TEACHER`.

#### Scenario: Student calls a teacher endpoint
- **WHEN** a `STUDENT` requests `GET /api/teachers`
- **THEN** the system responds 403

### Requirement: Current user
The system SHALL return the authenticated user's id, name, email, role, `mustChangePassword` and tenant (slug, name, time zone, kind) through `GET /api/auth/me`.

#### Scenario: Authenticated request
- **WHEN** a logged-in user requests `GET /api/auth/me`
- **THEN** the system responds 200 with their data and never includes the password hash

#### Scenario: Private teacher
- **WHEN** the teacher of an individual space requests `GET /api/auth/me`
- **THEN** `tenant.kind` is `INDIVIDUAL`

### Requirement: Change password
The system SHALL let an authenticated user change their password through `POST /api/auth/change-password` given the current password and a new one of at least 8 characters, clearing `mustChangePassword`.

#### Scenario: Successful change
- **WHEN** the user sends the correct current password and a valid new password
- **THEN** the system responds 204, the new password works for login, and `mustChangePassword` becomes false

#### Scenario: Wrong current password
- **WHEN** the current password is wrong
- **THEN** the system responds 400 with an `errors.currentPassword` entry and the password is unchanged

#### Scenario: New password too short
- **WHEN** the new password has fewer than 8 characters
- **THEN** the system responds 400 with an `errors.newPassword` entry
