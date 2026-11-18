# student-flashcards Specification

## Purpose
TBD - created by archiving change 14-prevent-duplicate-flashcards. Update Purpose after archive.

## Requirements

### Requirement: No duplicate cards per student
A student SHALL NOT have two cards with the same normalized front: the front trimmed, inner runs of whitespace collapsed to one space, and lowercased (accents are kept). Adding a single card (`POST /api/students/{id}/cards`) or editing a card (`PUT /api/cards/{id}`) to a front the student already has on another card SHALL respond 409. Adding several cards (`POST /api/students/{id}/cards/batch`) SHALL skip every card whose front duplicates one of the student's cards or an earlier card of the same list, add the others, and respond with `added` (the new cards) and `skipped` (the skipped fronts as sent); when every card is skipped the system SHALL respond 409. The rule SHALL hold under concurrent requests.

#### Scenario: Same word in another case
- **WHEN** a student has a card "Apple" and the teacher adds a card " apple "
- **THEN** the system responds 409 and the student still has one card

#### Scenario: Edit into a duplicate
- **WHEN** the teacher edits the front of a card to the front of another card of the same student
- **THEN** the system responds 409 and the card is unchanged

#### Scenario: Batch with duplicates
- **WHEN** the student has "apple" and the teacher adds the list "apple", "pear", "Pear", "plum"
- **THEN** the system adds "pear" and "plum" and reports "apple" and "Pear" as skipped

#### Scenario: Different accents
- **WHEN** the student has "cafe" and the teacher adds "café"
- **THEN** the card is added

#### Scenario: Another student
- **WHEN** a student has "apple" and the teacher adds "apple" to another student
- **THEN** the card is added
