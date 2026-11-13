# Spec Delta

## ADDED Requirements

### Requirement: Space status
Each tenant SHALL have a status: `PENDING`, `ACTIVE` or `SUSPENDED`. Tenants provisioned by the operator SHALL start `ACTIVE`; spaces created by a private teacher's sign-up SHALL start `PENDING`. The status SHALL be part of the tenant summary returned by login, `/api/auth/me` and `GET /api/tenant`.

#### Scenario: New private space
- **WHEN** a private teacher signs up
- **THEN** the space's status is `PENDING`

#### Scenario: Provisioned school
- **WHEN** the operator provisions a school
- **THEN** its status is `ACTIVE`

### Requirement: Inactive spaces cannot be used
For a tenant that is not `ACTIVE`, the system SHALL answer every tenant-scoped request with 403 and `code: ACCOUNT_INACTIVE` (and the tenant's `status`), except `/api/auth/**` and `GET /api/tenant`. The status SHALL be checked on every request, so a change applies to existing sessions.

#### Scenario: Pending teacher logs in
- **WHEN** the teacher of a pending space with a confirmed email logs in
- **THEN** login succeeds and `/api/auth/me` shows status `PENDING`

#### Scenario: Pending teacher uses the space
- **WHEN** that teacher calls `GET /api/students`
- **THEN** the system responds 403 with `code: ACCOUNT_INACTIVE`

#### Scenario: Activated with an existing session
- **WHEN** the space is activated and the teacher calls `GET /api/students` with the same token
- **THEN** the system responds 200

### Requirement: Operator activation
The platform operator SHALL be able, with the `X-Platform-Key` header, to list spaces (optionally by status, oldest first, with the owner's name, email and whether it is confirmed), activate a space and suspend a space. Activation SHALL be refused with 422 while no staff user of the space has confirmed the email. Activating an active space or suspending a suspended one SHALL change nothing.

#### Scenario: Activation before email confirmation
- **WHEN** the operator activates a pending space whose teacher has not confirmed the email
- **THEN** the system responds 422 and the space stays `PENDING`

#### Scenario: Suspension
- **WHEN** the operator suspends an active school
- **THEN** its users get 403 `ACCOUNT_INACTIVE` until it is activated again

### Requirement: Activation emails
When the teacher of a pending space confirms the email, the system SHALL email the operator address (`educare.mail.operator`), if configured. When a private space is activated, the system SHALL email its teacher with the space's login link. Emails SHALL be sent after the change commits.

#### Scenario: Space activated
- **WHEN** the operator activates a private space
- **THEN** the teacher receives "Seu espaço na Educare está liberado" with the login link

### Requirement: Platform key attempts
The system SHALL limit wrong platform keys to 10 per client IP in 15 minutes, answering 429 with `Retry-After` after that. The correct key SHALL keep working.

#### Scenario: Guessing the key
- **WHEN** a client sends an 11th wrong key within 15 minutes
- **THEN** the system responds 429
