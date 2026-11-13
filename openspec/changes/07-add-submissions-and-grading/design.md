# Design

## Context

Change 06 created `assignments` with status `ASSIGNED | SUBMITTED | GRADED` and ordering rules.

## Goals / Non-Goals

**Goals:** a single current answer per assignment; precise scores; history of first submission time.
**Non-Goals:** file uploads, multiple attempts with history, returning work for redo, rubric-based grading, notifications.

## Decisions

- **1:1 table** `submissions(id, tenant_id, assignment_id unique, answer text, instructions_snapshot text, submitted_at, updated_at, late, score numeric(3,1), feedback text, graded_at, graded_by)`. Grade lives on the submission because it grades that answer. Alternative: grade columns on `assignments` - mixes lifecycle data and allows a grade without an answer.
- **Score as `BigDecimal`/`numeric(3,1)`**, validated with `@DecimalMin("0") @DecimalMax("10") @Digits(integer = 2, fraction = 1)`. Avoids float rounding.
- **Status transitions** in one service method per action with explicit guards (state machine table in code): submit allowed from ASSIGNED/SUBMITTED; grade from SUBMITTED/GRADED.
- **Queue ordering**: when `status=SUBMITTED`, sort by `submitted_at` ascending (overrides change 06 default order).
- **Concurrency**: optimistic locking (`@Version`) on submission so a student edit racing a grade yields 409 instead of grading a different answer than the teacher read.
