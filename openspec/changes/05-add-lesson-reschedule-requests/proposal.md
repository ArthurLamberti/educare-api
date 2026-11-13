# Proposal

## Why

Students sometimes cannot attend a scheduled lesson. The school rule is that a student may ask to move a lesson only if they ask at least 2 days before it starts, and the teacher decides. Today only the teacher can change the time, and there is no way for the student to ask.

## What Changes

- A student can request a new time for one of their scheduled lessons, with an optional reason, only when the request is made at least 48 hours before the lesson's current start.
- The teacher sees pending requests for their lessons and approves or rejects them (optional rejection note). Approval moves the lesson, applying the normal overlap rules.
- One pending request per lesson. The student can withdraw a pending request.
- Pending requests are closed automatically when the lesson is canceled or the teacher changes its time directly.

## Capabilities

### New Capabilities
- `lesson-reschedule-requests`: student-initiated requests to move a lesson, with the 2-day rule and teacher approval.

### Modified Capabilities
None. (`lessons` is not archived yet; its time-change and cancel operations gain a side effect described in the new capability.)

## Impact

- New `reschedule` package; migration `V5__create_reschedule_requests.sql`.
- Uses the lesson `reschedule` path from change 04.
- educare-web change `05-add-lesson-reschedule-requests` adds the request and approval UI.
