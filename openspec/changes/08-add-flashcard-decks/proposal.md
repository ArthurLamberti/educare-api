# Proposal

## Why

Teachers want students to memorize vocabulary and facts between lessons with flashcards. Before students can review (change 09), teachers need to write decks of cards and propose them to their students.

## What Changes

- **Decks**: a teacher's set of flashcards with title and description. Teachers create, list, read, update and archive decks.
- **Cards**: front/back text in a deck, ordered, added/edited/removed by the deck's teacher.
- **Deck assignment**: the teacher proposes a deck to one or more of their students and can remove it later; students list their decks and read the cards.

## Capabilities

### New Capabilities
- `flashcard-decks`: teacher-authored decks and cards and their assignment to students.

### Modified Capabilities
None.

## Impact

- New `flashcard` package; migration `V8__create_flashcard_decks.sql`.
- educare-web change `08-add-flashcard-decks` adds the deck editor and "my decks" pages.
