# Tasks

## 1. Scheduling core

- [x] 1.1 Implement the pure scheduling function (ease floor, AGAIN/HARD/GOOD/EASY rules, day-aligned due times in a zone) and verify unit tests cover every scenario in the scheduling spec
- [x] 1.2 Add migration `V9__create_card_reviews.sql` (state and events tables, unique and due indexes, cascade on card delete) and verify it applies

## 2. Student review

- [x] 2.1 Implement `POST /api/reviews` (assigned-deck check, upsert state with row lock, write event, return new `dueAt`/`intervalDays`); verify tests for rating a new card, unassigned deck (404) and two consecutive ratings
- [x] 2.2 Implement `GET /api/reviews/queue` (due first, then new cards up to 20 per deck per school day, counts, 200 cap); verify tests for due-first ordering, allowance used, and unassigned deck (404) with a fixed clock

## 3. Progress

- [x] 3.1 Implement `GET /api/decks/{id}/progress` for teachers and `GET /api/reviews/progress` for students; verify tests for counters (new/learning/mature/due today/last 7 days) and other teacher's deck (404)

## 4. Documentation

- [x] 4.1 Document the review endpoints and scheduling rules in `README.md` and verify the curl examples run
