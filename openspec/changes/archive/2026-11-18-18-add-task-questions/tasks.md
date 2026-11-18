# Tasks

- [x] V22: `task_questions`, `submission_answers`, `assignment_draft_answers`
- [x] `TaskRequest.questions` (replace the whole list; max 50; instructions optional when there are questions); task responses with `questions`
- [x] Submission: validate `answers` against the task questions (first submission) or the snapshot (later ones): one per question in order, wrong count or blank → 400 `errors.answers`
- [x] Snapshot questions with the answers at the first submission; responses with `answers`
- [x] Draft (change 15) accepts and returns `answers`
- [x] Tests: task with questions and no instructions, submit all answers, missing answer, task edited after submission keeps the snapshot, draft with blank answers, task without questions unchanged
