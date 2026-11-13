# Spec Delta

## ADDED Requirements

### Requirement: Default platform admin
At startup the system SHALL create a platform admin with the email in `educare.platform.admin-email` (default `arthurlamberti001@gmail.com`) when none exists with it, with a random password. It SHALL never create a second one.

#### Scenario: First start
- **WHEN** the API starts with no platform admin for the configured email
- **THEN** one is created, and logging in with any password answers 401 until a password is set through a reset link

### Requirement: Platform admin login
`POST /api/platform/auth/login` SHALL return a token for an active platform admin with the right password (401 otherwise, 429 past 10 attempts per IP in 15 minutes). The token SHALL work on `/api/platform/**` and SHALL be rejected (401) on tenant-scoped endpoints; school tokens SHALL be rejected on `/api/platform/**`. `/api/platform/**` SHALL keep accepting the platform key.

#### Scenario: Admin token in a school
- **WHEN** a platform admin token is sent to `/api/auth/me` of a school
- **THEN** the system responds 401
