# Design

## Context

Change 08 provides decks, cards and `deck_students`. The school time zone (change 02) defines "today". The injectable `Clock` exists since change 05.

## Goals / Non-Goals

**Goals:** deterministic, testable scheduling; fast queue queries.
**Non-Goals:** FSRS or ML-based scheduling, per-student settings (new cards/day), offline review, undo of a rating.

## Decisions

- **State table** `card_reviews(id, tenant_id, student_id, card_id, ease numeric(4,2), interval_days int, repetitions int, due_at timestamptz, last_reviewed_at, introduced_on date, unique(student_id, card_id))` created lazily on the first rating; a card without a row is "new". FK to `cards` with `ON DELETE CASCADE`. Index on `(student_id, due_at)`.
- **Review log** `review_events(id, tenant_id, student_id, card_id, rating, reviewed_at, interval_days_after)` for the "reviews in the last 7 days" counter and future analytics. Cheap to keep and avoids recomputing from state.
- **Algorithm** as a pure function `schedule(state, rating, now, zone) -> state` in its own class, fully unit-tested against the spec table. SM-2 variant with four buttons (Anki-like) chosen over FSRS for simplicity and explainability.
- **Daily new-card allowance** counted from `card_reviews.introduced_on = today(school zone)` per deck; computed at queue time, not stored as a counter.
- **Day-aligned due times** for intervals >= 1 day: start of `today + interval` in the school zone, so a card is due at the start of the day rather than at the exact time it was last reviewed.
- **Queue size**: due cards are capped at 200 per request to keep responses small; the web app requests again when it runs out.
- **Concurrency**: rating uses an upsert on `(student_id, card_id)`; two rapid ratings of the same card apply sequentially (row lock), the second building on the first.

## Risks / Trade-offs

- [Students skipping days accumulate many due cards] → Cap per request; no penalty beyond SM-2's natural behavior.
- [Time zone change of a school shifts "today"] → Rare; due dates already stored as instants remain valid.
