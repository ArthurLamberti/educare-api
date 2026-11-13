# flashcard-decks Specification

## Purpose
Lets teachers author flashcard decks and propose them to their students, and lets students see the decks proposed to them.

## Requirements

### Requirement: Manage decks
The system SHALL let a `TEACHER` create (`POST /api/decks`), list (`GET /api/decks`, paginated, newest first), read, update and archive/unarchive decks with `title` (required, max 200) and `description` (optional, max 2000). Teachers SHALL only access their own decks (others → 404). Archived decks SHALL be hidden from the default list and SHALL not be assignable; students keep reviewing archived decks already assigned.

#### Scenario: Create deck
- **WHEN** a teacher posts a valid title
- **THEN** the system responds 201 with the deck and `cardCount` 0

#### Scenario: Another teacher's deck
- **WHEN** a teacher reads another teacher's deck
- **THEN** the system responds 404

### Requirement: Manage cards
The system SHALL let the deck's teacher add (`POST /api/decks/{id}/cards`), update (`PUT /api/cards/{id}`), delete (`DELETE /api/cards/{id}`) and reorder (`PUT /api/decks/{id}/cards/order` with the full list of card ids) cards with `front` and `back` (each required, max 1000). Cards SHALL be returned in their order. A deck SHALL hold at most 1000 cards.

#### Scenario: Add card
- **WHEN** the teacher adds a card with front and back
- **THEN** the system responds 201 and the card is last in the deck

#### Scenario: Blank side
- **WHEN** the back is blank
- **THEN** the system responds 400 with an `errors.back` entry

#### Scenario: Reorder with wrong ids
- **WHEN** the reorder list is missing a card or contains a card from another deck
- **THEN** the system responds 400 and the order is unchanged

### Requirement: Propose a deck to students
The system SHALL let the deck's teacher assign the deck to one or more of their active students (`POST /api/decks/{id}/students` with `studentIds`) and remove it from a student (`DELETE /api/decks/{id}/students/{studentId}`). Assigning a deck a student already has SHALL be ignored (idempotent). The teacher SHALL be able to list the students of a deck.

#### Scenario: Assign deck
- **WHEN** the teacher assigns a deck to two of their students
- **THEN** both students see the deck in their list

#### Scenario: Student not owned
- **WHEN** `studentIds` contains another teacher's student
- **THEN** the system responds 400 and assigns nothing

#### Scenario: Remove deck from student
- **WHEN** the teacher removes the deck from a student
- **THEN** the student no longer sees the deck

### Requirement: Student deck access
The system SHALL let a `STUDENT` list the decks assigned to them (`GET /api/decks`) and read their cards, and SHALL respond 404 for decks not assigned to them and 403 for any write.

#### Scenario: Student lists decks
- **WHEN** a student requests `GET /api/decks`
- **THEN** the response contains only decks assigned to them, with title, description, teacher name and card count
