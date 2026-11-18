# Spec Delta

## ADDED Requirements

### Requirement: Task categories
A `TEACHER` SHALL manage their own task categories through `GET /api/task-categories` (alphabetical, with the number of tasks using each), `POST /api/task-categories`, `PUT /api/task-categories/{id}` and `DELETE /api/task-categories/{id}`, with `name` required and at most 60 characters. Names SHALL be unique per teacher ignoring case and surrounding spaces (a duplicate SHALL respond 409), and a teacher SHALL have at most 100 categories. Other teachers' categories SHALL respond 404. A task SHALL accept an optional `categoryId` that SHALL be one of its teacher's categories, otherwise the system SHALL respond 400 with `errors.categoryId`. Deleting a category SHALL leave its tasks without a category. Task and assignment responses SHALL include the category (id, name), and `GET /api/tasks` and `GET /api/assignments` SHALL accept a `categoryId` filter.

#### Scenario: Create a category
- **WHEN** a teacher posts the name "Grammar"
- **THEN** the system responds 201 with the category

#### Scenario: Duplicate name
- **WHEN** the teacher has "Grammar" and posts " grammar "
- **THEN** the system responds 409

#### Scenario: Another teacher's category
- **WHEN** a teacher sets a task's `categoryId` to a category of another teacher
- **THEN** the system responds 400 with an `errors.categoryId` entry

#### Scenario: Delete a category in use
- **WHEN** the teacher deletes a category used by two tasks
- **THEN** the system responds 204 and both tasks have no category

#### Scenario: Student
- **WHEN** a student posts to `/api/task-categories`
- **THEN** the system responds 403
