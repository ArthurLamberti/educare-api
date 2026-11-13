# Design

## Context

Changes 02-03 provide tenants, roles, and students owned by teachers. Tenants have an IANA time zone.

## Goals / Non-Goals

**Goals:**
- Correct overlap detection under concurrent requests.
- Time stored and exchanged unambiguously.

**Non-Goals:**
- Recurring lessons, availability calendars, video-call links, attendance, notifications/e-mail (candidates for later changes).

## Decisions

- **Storage**: `lessons(id, tenant_id, teacher_id → users, student_id → students, starts_at timestamptz, duration_minutes int check 15..240, ends_at timestamptz, topic varchar(200), notes varchar(2000), status varchar check, created_at, updated_at)`. `ends_at` is stored (kept in sync by the entity) so overlap checks and indexes are simple.
- **Instants in the API**: `startsAt`/`endsAt` are `Instant` (UTC, ISO-8601). The web app converts using the tenant time zone. Alternative: local date-time + zone per lesson - unnecessary while each school has one zone.
- **Overlap rule**: `existing.starts_at < new.ends_at AND existing.ends_at > new.starts_at AND status = 'SCHEDULED'`, checked for teacher and for student, excluding the lesson being updated.
- **Concurrency**: PostgreSQL exclusion constraints (`btree_gist`, `EXCLUDE USING gist (teacher_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (status = 'SCHEDULED')`, and the same for `student_id`) guarantee no overlap even with concurrent requests. The service still checks first to return a clear 409 message; a constraint violation from a race is also mapped to 409. Alternative: application-level locking - weaker and more code.
- **Authorization**: teacher-only writes (`@PreAuthorize`), ownership via `teacher_id = currentUser`; student reads filtered by `student.user_id = currentUser`. Not visible → 404.
- **Hook for change 05**: lesson time changes go through one service method (`reschedule`) so the reschedule-request flow can reuse validation and overlap checks.

## Risks / Trade-offs

- [`btree_gist` extension must exist] → `CREATE EXTENSION IF NOT EXISTS btree_gist` in V4; PostgreSQL images include it. Testcontainers covers it.
- [Lessons crossing DST changes] → Instants make duration exact; display is the client's concern.
