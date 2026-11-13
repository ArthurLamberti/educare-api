# Tasks

## 1. Data model

- [x] 1.1 Add migration `V8__create_flashcard_decks.sql` (decks, cards, deck_students) and verify it applies
- [x] 1.2 Create entities, repositories and DTOs (deck with `cardCount`, card, deck student); verify the project compiles

## 2. Teacher authoring

- [x] 2.1 Implement deck CRUD with ownership and archive/unarchive; verify tests for create, another teacher's deck (404) and archived decks hidden by default
- [x] 2.2 Implement card add/update/delete/reorder with the 1000-card limit; verify tests for add (appended last), blank side (400), reorder with missing/foreign ids (400, unchanged)

## 3. Proposing to students

- [x] 3.1 Implement assign (idempotent, all-or-nothing ownership check), remove and list students of a deck; verify tests for assign, student not owned (400), duplicate assign ignored, remove, and archived deck not assignable (409)
- [x] 3.2 Implement student access to `GET /api/decks` and deck cards; verify tests that a student sees only assigned decks, gets 404 for others and 403 for writes

## 4. Documentation

- [x] 4.1 Document deck, card and assignment endpoints in `README.md` and verify the curl examples run
