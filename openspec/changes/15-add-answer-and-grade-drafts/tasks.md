# Tasks

- [ ] V19: answer draft columns on `assignments`, grade draft columns on `submissions`
- [ ] `PUT /api/assignments/{id}/draft` (assigned student; `ASSIGNED`/`SUBMITTED`; 409 when `GRADED`)
- [ ] Submit clears the answer draft; editing a submitted answer keeps working until graded
- [ ] `PUT /api/assignments/{id}/grade-draft` (task's teacher; `SUBMITTED`/`GRADED`; score rules as in grading when present; stale `version` → 409)
- [ ] Grade clears the grade draft
- [ ] Responses: `draft` only to the student, `gradeDraft` only to the teacher, `hasGradeDraft` in lists of the teacher
- [ ] Tests: student draft hidden from teacher and admin, submit clears draft, edit after submit, edit after grade (409), grade draft hidden from student, revision draft keeps published grade, stale version
