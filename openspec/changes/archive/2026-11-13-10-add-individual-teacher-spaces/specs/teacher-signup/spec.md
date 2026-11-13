# Spec Delta

## Purpose

Lets private teachers create their own space (an individual tenant) without the platform operator, with email confirmation and protection against abuse.

## ADDED Requirements

### Requirement: Address availability
The system SHALL report through `GET /api/public/slug-availability?slug=`, without tenant header or token, whether a slug can be used, answering `available` and, when not available, a `reason`: `INVALID` (not 3-40 lowercase letters, digits or hyphens), `RESERVED` (in the reserved list) or `TAKEN`.

#### Scenario: Free address
- **WHEN** a client checks `ana-ingles` and no tenant uses it
- **THEN** the system responds 200 with `available: true`

#### Scenario: Reserved address
- **WHEN** a client checks `www`
- **THEN** the system responds 200 with `available: false` and `reason: RESERVED`

#### Scenario: Taken address
- **WHEN** a client checks `demo` and the tenant `demo` exists
- **THEN** the system responds 200 with `available: false` and `reason: TAKEN`

### Requirement: Private teacher sign-up
The system SHALL accept through `POST /api/public/signups`, without tenant header or token, a teacher name, email, password (at least 8 characters), space name, slug and IANA time zone, and SHALL create in one transaction an `INDIVIDUAL` tenant and its only user with role `TEACHER`, not yet confirmed, then send a confirmation email with a single-use link valid for 48 hours. It SHALL respond 202 with the slug. A sign-up for a slug whose individual space is still unconfirmed, sent with that account's password, SHALL replace the earlier sign-up's name, email, space name and time zone and send a new link (so a mistyped email can be corrected); without the right password it SHALL respond 409.

#### Scenario: Successful sign-up
- **WHEN** a visitor signs up with valid data and a free slug `ana-ingles`
- **THEN** the system responds 202, the tenant `ana-ingles` exists with kind `INDIVIDUAL`, and a confirmation email is sent to the given address

#### Scenario: Address taken
- **WHEN** the slug is already used or reserved
- **THEN** the system responds 409 (taken) or 400 with `errors.slug` (invalid or reserved) and creates nothing

#### Scenario: Correcting a mistyped email
- **WHEN** a visitor signed up `ana-ingles` with `ana@mial.com`, and signs up `ana-ingles` again with `ana@mail.com` and the same password before confirming
- **THEN** the system responds 202, the first link stops working and a new link is sent to `ana@mail.com`

#### Scenario: Invalid data
- **WHEN** the email is malformed, the password has fewer than 8 characters or the time zone is unknown
- **THEN** the system responds 400 with an `errors` entry per invalid field and sends no email

### Requirement: Email confirmation
The system SHALL confirm the account through `POST /api/public/signups/confirm` with a valid, unused, unexpired token, mark the token used and respond 200 with the slug. Invalid, used or expired tokens SHALL get 400 without telling which case applies.

#### Scenario: Valid link
- **WHEN** the teacher posts the token from the email within 48 hours
- **THEN** the system responds 200 with the slug and the teacher can log in

#### Scenario: Used or expired link
- **WHEN** the token was already used or is older than 48 hours
- **THEN** the system responds 400 with "This confirmation link is invalid or has expired"

### Requirement: Resend confirmation
The system SHALL accept `POST /api/public/signups/resend` with email and slug and, when an unconfirmed account matches, send a new link and invalidate the previous ones, at most 3 times per hour per account. It SHALL always respond 202, whether or not an account matches.

#### Scenario: Unknown account
- **WHEN** a client asks to resend for an email that has no unconfirmed account in that slug
- **THEN** the system responds 202 and sends nothing

### Requirement: Expiry of unconfirmed sign-ups
The system SHALL delete individual spaces whose teacher has not confirmed the email within 7 days of signing up, making the slug available again.

#### Scenario: Never confirmed
- **WHEN** 7 days pass after a sign-up without confirmation
- **THEN** the tenant and its user are deleted and the slug is reported available

### Requirement: Rate limits on public endpoints
The system SHALL limit sign-ups to 5 per hour per client IP and 3 per day per email, slug checks to 60 per minute per client IP, and confirmations to 20 per hour per client IP, responding 429 with a `Retry-After` header when exceeded. The client IP SHALL be taken from `X-Forwarded-For` only when the request comes from a configured trusted proxy.

#### Scenario: Too many sign-ups
- **WHEN** the same client IP posts a sixth sign-up within an hour
- **THEN** the system responds 429 with `Retry-After` and creates nothing
