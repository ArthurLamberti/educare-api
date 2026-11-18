# Tasks

- [ ] V22: `task_questions`, `submission_answers`, answer draft as JSON
- [ ] `TaskRequest.questions` (replace the whole list; max 50; instructions optional when there are questions); task responses with `questions`
- [ ] Submission: validate `answers` against the task questions (first submission) or the snapshot (later ones): one per question, unknown or missing → 400 `errors.answers`
- [ ] Snapshot questions with the answers at the first submission; responses with `answers`
- [ ] Draft (change 15) accepts and returns `answers`
- [ ] Tests: task with questions and no instructions, submit all answers, missing answer, task edited after submission keeps the snapshot, draft with blank answers, task without questions unchanged
