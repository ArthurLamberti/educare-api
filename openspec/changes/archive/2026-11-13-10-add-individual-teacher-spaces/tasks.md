# Tasks

## 1. Data and infrastructure

- [x] 1.1 Add migration `V12__individual_spaces.sql` (`tenants.kind` default `SCHOOL`, `users.email_confirmed_at` backfilled, `email_confirmations` with hashed token, expiry and used flag) and verify it applies on the dev database
- [x] 1.2 Add `spring-boot-starter-mail`, the `Mailer` interface with SMTP implementation and a recording test implementation, Mailpit in `compose.yaml`, and the `educare.web.*` / `educare.security.trusted-proxies` properties; verify a test email reaches Mailpit locally
- [x] 1.3 Add the reserved-slug list and use it in operator provisioning too; verify a test for provisioning `www` (400)

## 2. Sign-up

- [x] 2.1 Implement `GET /api/public/slug-availability` (invalid, reserved, taken, available) without tenant or token; verify tests for each reason
- [x] 2.2 Implement `POST /api/public/signups` (validation, `INDIVIDUAL` tenant + unconfirmed `TEACHER` in one transaction, confirmation email after commit); verify tests for success (email recorded, login refused), taken slug (409), invalid fields (400) and rollback sending no email
- [x] 2.3 Implement `POST /api/public/signups/confirm` and `/resend`; verify tests for valid, expired, used and unknown tokens, resend invalidating old tokens, and resend for an unknown email still answering 202
- [x] 2.4 Implement rate limits (client IP from trusted proxy, per-email limit, 429 with `Retry-After`); verify tests with a fixed clock
- [x] 2.5 Implement the hourly cleanup of unconfirmed sign-ups older than 7 days (`!test` profile) with the logic in a service method; verify a test that an expired sign-up is deleted and its slug becomes available, and a confirmed one is kept

## 3. Authentication and tenancy

- [x] 3.1 Refuse login of unconfirmed users with 403 `code: EMAIL_NOT_CONFIRMED` (wrong password still 401); verify tests
- [x] 3.2 Add `kind` to `GET /api/tenant` and `tenant.kind` to `GET /api/auth/me`; verify tests for both kinds
- [x] 3.3 Implement `PUT /api/tenant` (name, time zone) for school admins and individual-space teachers, 403 for a school's teachers and students; verify tests
- [x] 3.4 Verify that a private teacher can create students, lessons, series, tasks and flashcards, and gets 403 on `/api/teachers` (end-to-end test)

## 4. Documentation

- [x] 4.1 Document sign-up, confirmation, rate limits, tenant kinds, settings and Mailpit in `README.md` and verify the curl examples run
