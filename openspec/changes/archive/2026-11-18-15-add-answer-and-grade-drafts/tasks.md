# Tasks

- [x] V19: answer draft columns on `assignments`, grade draft columns on `submissions`
- [x] `PUT /api/assignments/{id}/draft` (assigned student; `ASSIGNED`/`SUBMITTED`; 409 when `GRADED`)
- [x] Submit clears the answer draft; editing a submitted answer keeps working until graded
- [x] `PUT /api/assignments/{id}/grade-draft` (task's teacher; `SUBMITTED`/`GRADED`; score rules as in grading when present; stale `version` → 409)
- [x] Grade clears the grade draft
- [x] Responses: `draft` only to the student, `gradeDraft` only to the teacher, `hasGradeDraft` in lists of the teacher
- [x] Tests: student draft hidden from teacher and admin, submit clears draft, edit after submit, edit after grade (409), grade draft hidden from student, revision draft keeps published grade, stale version
