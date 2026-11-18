# Tasks

- [x] Check production and local data for duplicate cards (normalized front per student)
- [x] V18: `cards.front_key`, fill it, merge existing duplicates, unique index `(tenant_id, student_id, front_key)`
- [x] `Card`: compute `frontKey` on create and edit (trim, collapse spaces, lowercase with `Locale.ROOT`)
- [x] `FlashcardService.add`/`update`: 409 on a duplicate front (also mapping the unique index violation to 409)
- [x] `FlashcardService.addAll`: skip duplicates (existing and inside the list), return `CardBatchResponse(added, skipped)`, 409 when nothing is left; the card limit counts only the cards added
- [x] `LessonWordService.sendToFlashcards`: mark every pending word as sent, return `LessonWordFlashcardsResponse(words, skipped)`
- [x] Tests: single add, edit into a duplicate, batch with duplicates inside and against existing cards, all duplicates, lesson words with an existing card, case and spaces
