# Spec Delta

## ADDED Requirements

### Requirement: Task questions
A task SHALL accept an optional ordered list of open `questions`, each with a `prompt` (required, max 2000), at most 50 per task, sent as the whole list on create and update. A task with at least one question SHALL accept empty `instructions`; a task without questions SHALL still require them. Task and assignment responses SHALL include the questions (id, position, prompt) in order.

#### Scenario: Task made of questions
- **WHEN** a teacher creates a task with a title, three questions and no instructions
- **THEN** the system responds 201 with the three questions in order

#### Scenario: No instructions and no questions
- **WHEN** a teacher creates a task without instructions and without questions
- **THEN** the system responds 400 with an `errors.instructions` entry

#### Scenario: Too many questions
- **WHEN** a teacher sends 51 questions
- **THEN** the system responds 400 with an `errors.questions` entry
