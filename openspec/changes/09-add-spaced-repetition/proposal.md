# Proposal

## Why

Flashcards only work for memorization when each card comes back right before it would be forgotten. Students need a daily review queue driven by spaced repetition, and teachers want to see whether their students are keeping up.

## What Changes

- Each student has their own review state per card (due time, interval, ease, repetitions).
- A review queue returns the cards due now for a student (optionally per deck), plus a daily limit of new cards.
- The student rates each card `AGAIN`, `HARD`, `GOOD` or `EASY`; the system schedules the next review with an SM-2-style algorithm.
- Teachers see per-student progress on their decks (cards new/learning/mature, due today, reviews in the last 7 days).

## Capabilities

### New Capabilities
- `flashcard-review`: per-student spaced-repetition scheduling, review queue, ratings and progress statistics.

### Modified Capabilities
None.

## Impact

- New `review` package; migration `V9__create_card_reviews.sql`.
- educare-web change `09-add-spaced-repetition` adds the study session and progress views.
