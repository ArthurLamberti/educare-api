# flashcard-review Specification

## Purpose
Schedules flashcard reviews per student with spaced repetition, so each card is shown again at growing intervals based on how well the student remembered it, and reports progress to teachers.

## Requirements

### Requirement: Review queue
The system SHALL return to a `STUDENT`, through `GET /api/reviews/queue` (optional `deckId`), the cards from their assigned decks that are due (`dueAt` <= now), oldest due first, followed by up to the remaining daily allowance of new (never reviewed) cards in deck order. The daily allowance SHALL be 20 new cards per deck per day in the school's time zone. The response SHALL include the counts of due and new cards.

#### Scenario: Due cards first
- **WHEN** a student has 3 due cards and 30 new cards in one deck, and has introduced no new cards today
- **THEN** the queue has the 3 due cards followed by 20 new cards

#### Scenario: New card allowance used
- **WHEN** the student already introduced 20 new cards of that deck today
- **THEN** the queue contains only due cards

#### Scenario: Deck not assigned
- **WHEN** a student requests the queue for a deck that is not assigned to them
- **THEN** the system responds 404

### Requirement: Rate a card
The system SHALL accept a rating for a card through `POST /api/reviews` with `cardId` and `rating` (`AGAIN`, `HARD`, `GOOD`, `EASY`) from a student who has that card's deck assigned, update the card's schedule, and respond with the new `dueAt` and `intervalDays`.

#### Scenario: Card from unassigned deck
- **WHEN** a student rates a card of a deck not assigned to them
- **THEN** the system responds 404

### Requirement: Scheduling algorithm
The system SHALL schedule with these rules. Every card starts with ease 2.5, interval 0 days and 0 repetitions. Ease never goes below 1.3.
- `AGAIN`: repetitions = 0, interval = 0, ease − 0.20, due in 10 minutes.
- `HARD`: ease − 0.15; interval = 1 day if repetitions was 0, otherwise max(1, round(interval × 1.2)); repetitions + 1.
- `GOOD`: interval = 1 day on the first success, 6 days on the second, otherwise round(interval × ease); repetitions + 1.
- `EASY`: ease + 0.15; interval = 4 days on the first success, otherwise round(interval × ease × 1.3); repetitions + 1.
Due time for intervals of 1 day or more SHALL be the start of that day in the school's time zone (so cards are due "on a day", not at a time).

#### Scenario: New card rated GOOD
- **WHEN** a student rates a new card `GOOD`
- **THEN** its interval is 1 day and it is due at the start of tomorrow in the school's time zone

#### Scenario: Second GOOD
- **WHEN** a card with 1 repetition is rated `GOOD`
- **THEN** its interval becomes 6 days

#### Scenario: Mature card rated GOOD
- **WHEN** a card with interval 10 days, ease 2.5 and 3 repetitions is rated `GOOD`
- **THEN** its interval becomes 25 days

#### Scenario: Forgotten card
- **WHEN** a card with interval 25 days and ease 2.5 is rated `AGAIN`
- **THEN** repetitions become 0, ease becomes 2.3, and it is due again in 10 minutes

#### Scenario: Ease floor
- **WHEN** a card with ease 1.4 is rated `AGAIN`
- **THEN** its ease becomes 1.3

### Requirement: Progress for teachers
The system SHALL let the deck's teacher read per-student progress through `GET /api/decks/{id}/progress`: for each assigned student, the number of cards new (never reviewed), learning (interval < 21 days), mature (interval >= 21 days), due today, and reviews in the last 7 days. A student SHALL read their own progress through `GET /api/reviews/progress`.

#### Scenario: Teacher sees progress
- **WHEN** a teacher requests progress of a deck assigned to two students
- **THEN** the response has one entry per student with the five counters

#### Scenario: Other teacher's deck
- **WHEN** a teacher requests progress of another teacher's deck
- **THEN** the system responds 404
