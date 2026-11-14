# Spec Delta

## ADDED Requirements

### Requirement: Online lesson room link
A lesson and a weekly series SHALL accept an optional `meetingUrl`, which SHALL be an `http` or `https` link (surrounding spaces trimmed, empty meaning none, at most 500 characters); other values SHALL be rejected with 400 `errors.meetingUrl`. A lesson with a link is an online lesson. A series SHALL copy its link to every lesson it creates. The link SHALL be returned to everyone who can see the lesson.

#### Scenario: Unsafe link
- **WHEN** a teacher sends `meetingUrl` `javascript:alert(1)`
- **THEN** the system responds 400 with `errors.meetingUrl`

#### Scenario: Series link
- **WHEN** a teacher creates a weekly series with a room link
- **THEN** every generated lesson has that link

### Requirement: Lesson notes
The teacher of a lesson SHALL be able to set its notes (`annotations`, free text up to 10000 characters, empty clears them) through `PUT /api/lessons/{id}/annotations`, at any time and in any status. The system SHALL record when they last changed. Other users SHALL be refused. The lesson's student and the school's admins SHALL see the notes in the lesson.

#### Scenario: Notes after the lesson
- **WHEN** the teacher writes notes after the lesson ended
- **THEN** the system saves them and the student sees them in `GET /api/lessons/{id}`

#### Scenario: Someone else
- **WHEN** another teacher, the student or an admin sends notes
- **THEN** the system refuses with a 4xx status
