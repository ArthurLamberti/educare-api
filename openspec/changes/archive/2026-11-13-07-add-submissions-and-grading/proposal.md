# Proposal

## Why

Assignments are only useful if students can answer them and teachers can correct them. Students need to submit their answer, teachers need to grade it with a score and written feedback, and students need to see that feedback.

## What Changes

- A student submits a text answer to their assignment; they can edit it until it is graded. Late submissions are allowed and flagged.
- The assignment moves `ASSIGNED` → `SUBMITTED` → `GRADED`.
- The teacher grades a submitted assignment with a score from 0 to 10 (one decimal) and feedback text, and can revise the grade later.
- The student sees score and feedback once graded; the teacher has a queue of submissions awaiting grading.
- The task instructions are snapshotted at first submission, so later task edits do not change what was answered.

## Capabilities

### New Capabilities
- `assignment-submissions`: student answers, teacher grading and feedback visibility.

### Modified Capabilities
None. (`assignments` is not archived yet; status transitions to `SUBMITTED`/`GRADED` are defined in the new capability.)

## Impact

- New `submission` package; migration `V7__create_submissions.sql`.
- Assignment responses gain `submission` and `grade` fields when present.
- educare-web change `07-add-submissions-and-grading` adds answer, grading and feedback screens.
