# Spec Delta

## ADDED Requirements

### Requirement: Request a reset link
`POST /api/auth/forgot-password` (in a school) and `POST /api/platform/auth/forgot-password` SHALL always answer 202, and SHALL email a link valid 1 hour only when an active account with that email exists (for school users, with a confirmed email), at most 3 per account per hour. A new link SHALL invalidate older ones.

#### Scenario: Unknown email
- **WHEN** someone asks for a link for an email with no account
- **THEN** the system responds 202 and sends nothing

### Requirement: Reset the password
`POST /api/auth/reset-password` and `POST /api/platform/auth/reset-password` with `token` and `newPassword` (8-72) SHALL set the password, clear `mustChangePassword` (school users) and answer 204. An unknown, used or expired link, or a school user's link used in another school, SHALL answer 400 "This link is invalid or has expired".

#### Scenario: Link used twice
- **WHEN** a link that was already used is sent again
- **THEN** the system responds 400
