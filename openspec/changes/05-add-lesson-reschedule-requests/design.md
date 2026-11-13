# Design

## Context

Change 04 provides lessons with a single `reschedule` service path that validates and checks overlaps (backed by exclusion constraints).

## Goals / Non-Goals

**Goals:** enforce the 2-day rule on the server with a testable clock; keep lesson and request consistent.

**Non-Goals:** notifications (e-mail/push) about requests; counter-proposals by the teacher; multiple alternative times per request.

## Decisions

- **Table** `reschedule_requests(id, tenant_id, lesson_id, requested_by → users, original_starts_at, proposed_starts_at, reason, status, decision_note, decided_by, decided_at, created_at)`. `original_starts_at` records the time when the request was made, for history.
- **One pending per lesson**: partial unique index `ON reschedule_requests(lesson_id) WHERE status = 'PENDING'`; violation mapped to 409.
- **2-day rule** = `now <= lesson.startsAt - 48h`, using an injected `java.time.Clock` bean so tests can fix "now". 422 (Unprocessable Content) distinguishes a business-rule refusal from validation (400) and conflicts (409). The rule is about the current lesson time, not the proposed one; the proposed time only needs to be in the future and free.
- **Approval** in one transaction: lock the request row, verify `PENDING`, call `LessonService.reschedule(lesson, proposedStartsAt)` (which validates and checks overlaps), then mark `APPROVED`. Any failure rolls back, leaving it pending.
- **Auto-close**: `LessonService` publishes a `LessonTimeChanged`/`LessonCanceled` event (Spring application event, same transaction); a listener marks pending requests `CANCELED`. Approval itself must not cancel its own request, so the approval path marks it `APPROVED` before publishing, and the listener skips non-pending rows.

## Risks / Trade-offs

- [Time zone confusion about "2 days"] → Defined as 48 hours in absolute time and stated in the API error message; the UI shows the deadline in the school's zone.
- [Student requests a slot the teacher fills before approval] → Approval re-checks overlaps and answers 409; the teacher can reject with a note.
