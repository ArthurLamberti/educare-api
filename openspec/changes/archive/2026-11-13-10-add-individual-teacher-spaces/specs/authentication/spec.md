# Spec Delta

## MODIFIED Requirements

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

### Requirement: Current user
The system SHALL return the authenticated user's id, name, email, role, `mustChangePassword` and tenant (slug, name, time zone, kind) through `GET /api/auth/me`.

#### Scenario: Authenticated request
- **WHEN** a logged-in user requests `GET /api/auth/me`
- **THEN** the system responds 200 with their data and never includes the password hash

#### Scenario: Private teacher
- **WHEN** the teacher of an individual space requests `GET /api/auth/me`
- **THEN** `tenant.kind` is `INDIVIDUAL`
