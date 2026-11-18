# Proposal: Answer and grade drafts

## Why
Students write long answers in more than one sitting and today the only way to keep the text is to
submit it, which shows it to the teacher as finished. Teachers have the same problem with corrections:
saving a grade publishes it to the student at once.

## What Changes
- **Student draft.** `PUT /api/assignments/{id}/draft` with `answer` (may be blank, max 20000) saves
  the student's private draft while the assignment is `ASSIGNED` or `SUBMITTED`; the status does not
  change and the teacher never sees it. Submitting (`PUT .../submission`) publishes the text and clears
  the draft.
- **Edit until graded (kept).** After submitting, the student can still replace the answer until the
  teacher publishes a grade, either directly ("Reenviar") or through a draft first. While a draft
  exists, the teacher keeps seeing the last submitted version.
- **Teacher draft.** `PUT /api/assignments/{id}/grade-draft` with optional `score`, optional `feedback`
  and the reviewed `version` saves the teacher's private correction while the assignment is
  `SUBMITTED` or `GRADED`; the status does not change and the student never sees it. Publishing the
  grade (`PUT .../grade`) clears the draft. A draft on a `GRADED` assignment (a revision) keeps the
  published grade visible to the student until the revision is published.
- Responses: the student gets `draft { answer, savedAt }`; the task's teacher gets
  `gradeDraft { score, feedback, savedAt, version }`; each only for themselves. The grading queue marks
  items with a grade draft (`hasGradeDraft`).

## Impact
- Migration V19: `assignments.answer_draft`, `assignments.answer_draft_saved_at`;
  `submissions.draft_score`, `draft_feedback`, `grade_draft_saved_at`, `grade_draft_version`.
- `AssignmentResponse` gains `draft`, `gradeDraft`, `hasGradeDraft`.
- Web (educare-web change 15): "Salvar rascunho"/"Enviar" for students, "Salvar correção"/"Enviar
  correção" for teachers.
