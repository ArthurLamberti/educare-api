# Spec Delta

## ADDED Requirements

### Requirement: Answer each question
For a task with questions, submitting SHALL require `answers` with exactly one entry per question (`questionId`, `answer` required, max 5000), and the general `answer` SHALL become optional; unknown, repeated or missing questions SHALL respond 400 with `errors.answers`. The first submission SHALL copy each question's prompt and position into the submission with its answer; later submissions SHALL answer those copied questions, so editing the task SHALL NOT change a submission. The answer draft SHALL also accept per-question answers, which may be blank. Submission responses SHALL include the answers in order with their prompts. The teacher SHALL grade the whole submission with the single score and feedback.

#### Scenario: All questions answered
- **WHEN** a student submits an answer for each of the three questions
- **THEN** the system responds 200 and the submission lists the three prompts with their answers

#### Scenario: Missing answer
- **WHEN** a student submits answers for two of three questions
- **THEN** the system responds 400 with an `errors.answers` entry

#### Scenario: Task edited after submission
- **WHEN** the teacher edits a question after the student submitted
- **THEN** the submission keeps the original prompt and the student's later edits answer the original questions

#### Scenario: Draft with blank answers
- **WHEN** a student saves a draft answering only the first question
- **THEN** the draft is saved and the other answers are blank
