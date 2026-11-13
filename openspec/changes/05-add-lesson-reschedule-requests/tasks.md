# Tasks

## 1. Data model and clock

- [ ] 1.1 Add migration `V5__create_reschedule_requests.sql` (table, status check, partial unique index for pending) and verify it applies
- [ ] 1.2 Register a `Clock` bean (UTC system clock) used by lesson and request services, and verify tests can override it with a fixed clock

## 2. Student operations

- [ ] 2.1 Implement `POST /api/lessons/{id}/reschedule-requests` with ownership, future time, 48-hour rule (422) and one-pending rule (409); verify tests for valid request, exactly 48h, 47h, another student's lesson and second pending request
- [ ] 2.2 Implement withdraw and role-scoped `GET /api/reschedule-requests` (filter by `status`, newest first); verify tests for withdraw and student/teacher/admin visibility

## 3. Teacher decisions

- [ ] 3.1 Implement approve (reusing `LessonService.reschedule`, transactional) and reject with note; verify tests for approve, approve into conflict (stays pending), reject and deciding twice (409)
- [ ] 3.2 Publish lesson time-changed/canceled events and close pending requests as `CANCELED`; verify tests for direct teacher time change and lesson cancel

## 4. Documentation

- [ ] 4.1 Document the request lifecycle and the 48-hour rule in `README.md` and verify the curl examples run
