# Proposal: Question blocks in tasks

## Why
Many exercises are a list of questions. Today the teacher numbers them inside the instructions and the
student answers everything in one text box, which is hard to write and to correct.

## What Changes
- A task gets an optional ordered list of open questions (`questions: [{ prompt }]`, prompt required,
  max 2000, at most 50). The list is sent whole on create and update. With questions, `instructions`
  become optional.
- Submitting (and saving a draft, change 15) accepts `answers: ["...", ...]`, one entry per question in
  order (max 5000 each; required on submit, may be blank in a draft). Answers are identified by
  position, not by question id: the submission keeps its own copy of the prompts, so ids would stop
  matching once the teacher edits the task. The general `answer`
  becomes optional for tasks with questions and stays required for tasks without them.
- At the first submission the questions are copied into the submission next to the answers, like the
  instructions snapshot. Later edits of the task do not change what the student answered; later
  resubmissions answer the snapshot questions.
- The teacher grades everything together with the single score and feedback (unchanged).
- Responses: tasks and assignments carry `questions [{ prompt }]` in order; submissions carry
  `answers [{ prompt, answer }]` in order; the student's draft carries `answers` too.

## Impact
- Depends on change 15 (the answer draft also stores per-question answers).
- Migration V22: `task_questions` (task, position, prompt), `submission_answers` (submission,
  position, prompt, answer) and `assignment_draft_answers` (assignment, position, answer).
- Web (educare-web change 18): question editor in the task form, one field per question when answering,
  questions and answers side by side when grading.
