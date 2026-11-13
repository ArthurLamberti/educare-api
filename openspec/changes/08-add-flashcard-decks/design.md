# Design

## Context

Teachers and owned students exist (changes 02-03). Change 09 attaches per-student review state to cards.

## Goals / Non-Goals

**Goals:** simple authoring; stable card ids so review history (change 09) survives edits and reordering.
**Non-Goals:** images/audio on cards, CSV/Anki import, shared decks between teachers, cloze cards.

## Decisions

- **Tables**: `decks(id, tenant_id, teacher_id, title, description, archived, created_at, updated_at)`, `cards(id, tenant_id, deck_id, front, back, position, created_at, updated_at)`, `deck_students(deck_id, student_id, assigned_at, primary key(deck_id, student_id))`.
- **Ordering** by integer `position`; reorder rewrites positions in one transaction from the full id list (validated as an exact permutation).
- **Card edits keep the id**, so a student's review progress stays with the card. Deleting a card deletes its review state (change 09 adds the FK with cascade).
- **Removing a deck from a student** keeps their review state (rows stay, but are not shown); re-assigning restores progress. Simpler and friendlier than deleting history.
- **`GET /api/decks` is role-aware**: same endpoint returns own decks for teachers and assigned decks for students, mirroring the lessons/assignments pattern.
