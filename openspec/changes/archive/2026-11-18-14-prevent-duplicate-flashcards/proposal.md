# Proposal: No duplicate flashcards

## Why
A student can end up with the same word twice (added by hand, from a list, and again from a lesson's
new words). Duplicates waste review time and split the progress of one word across two cards.

## What Changes
- Two cards of the same student are duplicates when their fronts match after trimming, collapsing
  inner spaces and lowercasing ("Apple", " apple " and "APPLE" are the same; accents count, so "cafe"
  and "café" differ).
- Adding one card (`POST /api/students/{id}/cards`) or editing one (`PUT /api/cards/{id}`) to a front
  the student already has answers 409.
- Adding several (`POST /api/students/{id}/cards/batch`) skips the duplicates (against the student's
  cards and inside the list) and adds the rest. The response changes from a list of cards to
  `{ added: Card[], skipped: string[] }` (the skipped fronts as sent). Only a list where every card is
  a duplicate answers 409.
- Sending a lesson's words to the flashcards skips words the student already has; they are still
  marked as sent. The response changes to `{ words: LessonWord[], skipped: string[] }`.
- A unique index on (student, normalized front) closes the race between two simultaneous adds.

## Impact
- Migration V18: `cards.front_key` (normalized front) filled for existing cards, unique index
  `(tenant_id, student_id, front_key)`. Existing duplicates are merged first: the card with the most
  review progress (latest `last_reviewed_at`, then oldest) is kept and the others are deleted.
  Production and local data are checked before deploying.
- Breaking response shapes of the batch and lesson-words flashcard endpoints (only educare-web calls them).
- Web (educare-web change 14): messages for the 409 and "N palavras já estavam nos flashcards".
- Note: `openspec/specs/flashcard-decks` still describes the old decks; per-student cards get their own
  capability `student-flashcards`, starting with this requirement.
