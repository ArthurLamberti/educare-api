# Tasks

## 1. Data model

- [x] 1.1 Add migration `V7__create_submissions.sql` (table, unique assignment, score check, version column) and verify it applies
- [x] 1.2 Create `Submission` entity (tenant-owned, `@Version`) and DTOs; extend assignment responses with `submission` and `grade`; verify the project compiles

## 2. Student submission

- [x] 2.1 Implement `PUT /api/assignments/{id}/submission` with status guards, instructions snapshot and `late` flag; verify tests for first submission, edit before grading, edit after grading (409), another student's assignment (404) and late submission with a fixed clock

## 3. Teacher grading

- [x] 3.1 Implement `PUT /api/assignments/{id}/grade` with score validation and revisions; verify tests for grade, grade before submission (409), invalid scores (10.5, -1, 7.25) and revising a grade
- [x] 3.2 Implement the `status=SUBMITTED` queue ordering and grade visibility rules; verify tests for queue order and that a student sees score/feedback only when `GRADED`

## 4. Documentation

- [x] 4.1 Document the submission/grading lifecycle in `README.md` and verify the curl examples run
