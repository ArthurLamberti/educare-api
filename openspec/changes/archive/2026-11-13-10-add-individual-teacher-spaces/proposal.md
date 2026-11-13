# Proposal

## Why

Educare only serves schools, and a school is created by the platform operator. Private teachers (one teacher, their own students) are a large share of language tutoring and cannot use the product without contacting us. They need to sign up on their own and get the same lessons, tasks and flashcards, without the school-only parts (admins, teacher management).

## What Changes

- A tenant gets a **kind**: `SCHOOL` (today's behavior) or `INDIVIDUAL` (one private teacher).
- **Public sign-up** for private teachers: name, email, password, space name, address (slug) and time zone create an `INDIVIDUAL` tenant whose only staff user is that teacher (role `TEACHER`, no admin). Slug availability can be checked before submitting.
- **Email confirmation**: the account cannot log in until the teacher opens the link sent by email. Unconfirmed sign-ups expire after 7 days and free the address. Confirmation emails can be resent.
- **Abuse protection** on the public endpoints: rate limits per client IP and per email, reserved slugs.
- **Space settings**: `PUT /api/tenant` changes the name and time zone; allowed to admins of a school and to the teacher of an individual space.
- `GET /api/auth/me` and `GET /api/tenant` include `tenant.kind`.
- In an `INDIVIDUAL` tenant, teacher-management endpoints are not available (there is no admin), and nothing else changes: lessons, series, tasks, grading and flashcards work as for any teacher.
- Email sending infrastructure (Spring Mail); Mailpit in the local Docker Compose to read emails in development.

## Capabilities

### New Capabilities
- `teacher-signup`: public sign-up of private teachers, slug availability, email confirmation, resend, expiry of unconfirmed sign-ups, rate limits.

### Modified Capabilities
- `tenancy`: tenant kind; space settings endpoint; kind in public tenant info.
- `authentication`: login refused until the email is confirmed; `tenant.kind` in the current user.

## Non-Goals

- Billing, plans or trial limits.
- A teacher working in several schools with one account (separate change: user ↔ tenant memberships).
- Turning an individual space into a school (adding an admin and other teachers); the data model allows it later.
- Sign-up of schools by themselves (still provisioned by the operator).
- Social login (Google) and password reset by email (the mail infrastructure added here makes the latter easy as a follow-up).

## Impact

- Migration `V12__individual_spaces.sql` (`tenants.kind`, `users.email_confirmed_at`, `email_confirmations` table).
- New `signup` package (controller, service, tokens, rate limiter, cleanup job) and `mail` package; `spring-boot-starter-mail`; Mailpit service in `compose.yaml`.
- Changes in `TenantFilter` (public sign-up paths need no tenant), `AuthService.login`, `TenantController`, `TeacherController`.
- educare-web change `10-add-individual-teacher-spaces` adds the sign-up and confirmation pages, settings page and wording for individual spaces.
