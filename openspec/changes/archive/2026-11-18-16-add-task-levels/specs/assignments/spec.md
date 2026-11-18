# Spec Delta

## ADDED Requirements

### Requirement: Task levels
The system SHALL keep the level scale as data: each level has a code, a name, a position and an active flag, and the initial scale SHALL be A1, A2, B1, B2, C1 and C2 in that order. `GET /api/levels` SHALL return the active levels in order to any signed-in user. A task SHALL accept an optional `levelId`, which SHALL be an active level, otherwise the system SHALL respond 400 with `errors.levelId`. A level deactivated later SHALL remain on the tasks that already have it. Task and assignment responses SHALL include the task's level (id, code, name), and `GET /api/tasks` and `GET /api/assignments` SHALL accept a `levelId` filter.

#### Scenario: Scale
- **WHEN** a teacher requests `GET /api/levels`
- **THEN** the response lists A1, A2, B1, B2, C1, C2 in order

#### Scenario: Task with a level
- **WHEN** a teacher creates a task with the level B1
- **THEN** the task and its assignments are returned with level B1

#### Scenario: Unknown level
- **WHEN** a teacher sends a `levelId` that does not exist or is inactive
- **THEN** the system responds 400 with an `errors.levelId` entry

#### Scenario: Filter
- **WHEN** a teacher lists tasks with `levelId` of A2
- **THEN** only their A2 tasks are returned
