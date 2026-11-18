# Spec Delta

## MODIFIED Requirements

### Requirement: New words of a lesson
The teacher of a lesson SHALL be able to add new words to it (`POST /api/lessons/{id}/words` with `words`: `word` up to 200 and `meaning` up to 1000, both required; all or nothing; at most 200 words per lesson, else 409) and remove them (`DELETE /api/lesson-words/{id}`), at any time and in any status. The lesson's teacher and student and the school's admins SHALL read them in order (`GET /api/lessons/{id}/words`); others SHALL get 404. Each word SHALL record when it was sent to the flashcards and to a task. `POST /api/lessons/{id}/words/flashcards` SHALL add one card (front: word, back: meaning) to the lesson's student for each word not yet sent to the flashcards, skipping words the student already has as a card (see No duplicate cards per student); skipped words SHALL also be marked as sent and the response SHALL list them in `skipped` next to the lesson's `words`. `POST /api/lessons/{id}/words/task` (optional future `dueAt`) SHALL create in the teacher's library a task asking for one sentence with each word not yet sent in a task, listing those words, and SHALL assign it to the lesson's student. Each word SHALL be sent at most once to each place; with nothing left to send the system SHALL respond 409. Removing a word SHALL keep the cards and tasks already sent.

#### Scenario: Send to flashcards
- **WHEN** the teacher sends a lesson's words to the flashcards
- **THEN** the student has one new card per word and the words record when they were sent

#### Scenario: Words added later
- **WHEN** the teacher adds a word after sending the others and sends again
- **THEN** only the new word is sent

#### Scenario: Sentence task
- **WHEN** the teacher sends the words as a task with a due date
- **THEN** the student has a new assignment with that due date whose instructions list the words

#### Scenario: Word already in the flashcards
- **WHEN** the student already has a card "apple" and the teacher sends the lesson words "Apple" and "pear"
- **THEN** only "pear" becomes a card, both words are marked as sent and "Apple" is reported as skipped
