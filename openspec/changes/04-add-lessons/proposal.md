# Proposal

## Why

Lessons are the core of the tutoring relationship: a teacher meets one student at a scheduled time. Teachers need to schedule, edit, reschedule and cancel lessons, and students need to see their upcoming lessons. Reschedule requests (change 05) build on this.

## What Changes

- New lesson: one teacher + one of their students, a start instant, a duration (15-240 minutes), a topic and optional notes, status `SCHEDULED` or `CANCELED`.
- Teachers create, list, read, update (including changing the time) and cancel their lessons.
- Lessons of the same teacher, or of the same student, cannot overlap.
- Students list and read their own lessons (read-only).
- Admins can list all lessons of the school.
- Listing filters by time range (`from`, `to`) and status, ordered by start time.

## Capabilities

### New Capabilities
- `lessons`: scheduling and managing 1:1 lessons between a teacher and a student.

### Modified Capabilities
None.

## Impact

- New `lesson` package; migration `V4__create_lessons.sql`.
- Times are UTC instants in the API; clients render them in the tenant time zone from `GET /api/auth/me`.
- educare-web change `04-add-lessons` adds the lesson pages.
