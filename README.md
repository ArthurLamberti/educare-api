# Educare API

REST API for Educare (Kotlin, Spring Boot 4, PostgreSQL). The web frontend lives in `../educare-web`.

## Requirements

- JDK 21
- Docker (PostgreSQL for development and for tests)

## Running

```bash
./gradlew bootRun
```

`bootRun` starts the PostgreSQL service from `compose.yaml` automatically (Spring Boot Docker Compose
support), stops it again when the API shuts down, and applies the database migrations. If the
container is already running it is reused and left running. The API listens on http://localhost:8080.

To manage the database yourself:

```bash
docker compose up -d      # start PostgreSQL (localhost:5432, db/user/password: educare)
docker compose down       # stop it (data is kept in the educare-postgres volume)
docker compose down -v    # stop it and delete all data
```

Connection settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.
Profiles: `dev` (default) and `prod` (`SPRING_PROFILES_ACTIVE=prod`).

## Schools, login and roles

Educare is multi-tenant: each school (tenant) has a slug that matches its web address
(`<slug>.educare.com`). Every `/api/**` request must name its school and, except login, carry an
access token:

| Header | Value |
|---|---|
| `X-Tenant` | school slug, e.g. `demo` (unknown or missing → 404) |
| `Authorization` | `Bearer <token>` from `POST /api/auth/login` (missing/invalid/expired → 401) |

Roles: `ADMIN` (manages the school's teachers), `TEACHER`, `STUDENT`. A wrong role gets 403.
Tokens last 12 hours and only work on the school that issued them. Data of one school is never
visible to another (Hibernate `@TenantId` on every tenant-owned entity, see `TenantOwnedEntity`).

In the `dev` profile the API seeds school `demo` with `admin@demo.com` / `password123`.

```bash
# log in
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'X-Tenant: demo' -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.com","password":"password123"}' | jq -r .token)

# current user and school
curl -s localhost:8080/api/auth/me -H 'X-Tenant: demo' -H "Authorization: Bearer $TOKEN"

# create a teacher (admin only); the teacher must change the password on first login
curl -s localhost:8080/api/teachers -H 'X-Tenant: demo' -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Ana","email":"ana@demo.com","initialPassword":"temp-pass-1"}'
```

Other endpoints: `GET /api/tenant` (public: school name and time zone), `POST /api/auth/change-password`, `GET|PUT /api/teachers/{id}`,
`POST /api/teachers/{id}/deactivate|activate`.

### Students

Students are user accounts (role `STUDENT`) owned by a teacher. Teachers create and see only their own
students; admins see everyone (`?teacherId=` filters) and must pass `teacherId` when creating, and can
reassign it on update. New students must change their initial password on first login.
`DELETE /api/students/{id}` deactivates (the student can no longer log in, history is kept; listed
again with `?includeInactive=true`); `POST /api/students/{id}/activate` restores access.

```bash
TEACHER=$(curl -s localhost:8080/api/auth/login -H 'X-Tenant: demo' -H 'Content-Type: application/json' \
  -d '{"email":"ana@demo.com","password":"temp-pass-1"}' | jq -r .token)

curl -s localhost:8080/api/students -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Maria","email":"maria@demo.com","birthDate":"2012-03-04","initialPassword":"maria-temp-1"}'
```

Response fields: `id`, `name`, `email`, `birthDate`, `teacherId`, `teacherName`, `active`, `createdAt`.

### Lessons

Lessons are 1:1 (one teacher, one student). **Times are UTC instants** (ISO-8601, e.g. `2026-11-20T13:00:00Z`);
clients convert to and from the school's `timeZone` (from `GET /api/tenant` or `/api/auth/me`).

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/lessons` | teacher | `studentId` (own active student), `startsAt` (future), `durationMinutes` (15-240), `topic`, `notes?` |
| `PUT /api/lessons/{id}` | lesson's teacher | `startsAt`, `durationMinutes`, `topic`, `notes?`; the student never changes |
| `POST /api/lessons/{id}/cancel` | lesson's teacher | status `CANCELED`; the record is kept and the slot is freed |
| `GET /api/lessons` | all | teachers see theirs, students theirs, admins all; filters `from`, `to` (`from <= startsAt < to`), `status`, `studentId`, `teacherId` (admin); ordered by `startsAt` (`sort=startsAt,desc` to reverse) |
| `GET /api/lessons/{id}` | all | 404 when the caller cannot see it |

A teacher or student cannot have two overlapping `SCHEDULED` lessons (409); back-to-back lessons are fine.
The database enforces this too (exclusion constraints), so concurrent requests cannot double-book.
Canceled lessons cannot be edited (409).

```bash
curl -s localhost:8080/api/lessons -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' \
  -d '{"studentId":1,"startsAt":"2030-01-15T13:00:00Z","durationMinutes":60,"topic":"Past simple"}'

curl -s "localhost:8080/api/lessons?from=$(date -u +%FT%TZ)&status=SCHEDULED" \
  -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER"
```

Response fields: `id`, `teacherId`, `teacherName`, `studentId`, `studentName`, `startsAt`, `endsAt`,
`durationMinutes`, `topic`, `notes`, `status`, `createdAt`, `updatedAt`.

### Lesson change requests

A student may ask to move one of their scheduled lessons, **at least 48 hours before the lesson's current
start** (otherwise 422). The lesson keeps its duration; the proposed time must be in the future and free.
Only one request per lesson can be pending (409). The lesson's teacher approves (the lesson moves, with the
usual overlap checks; a conflict answers 409 and the request stays pending) or rejects with an optional note.

```
PENDING ──approve──▶ APPROVED      (teacher; lesson moved)
        ──reject───▶ REJECTED      (teacher; optional note)
        ──withdraw─▶ WITHDRAWN     (student)
        ──────────▶ CANCELED       (lesson canceled or moved directly by the teacher)
```

| Endpoint | Who |
|---|---|
| `POST /api/lessons/{id}/reschedule-requests` `{"proposedStartsAt", "reason?"}` | the lesson's student |
| `GET /api/reschedule-requests?status=PENDING` | students: theirs; teachers: on their lessons; admins: all (newest first) |
| `POST /api/reschedule-requests/{id}/withdraw` | the requesting student |
| `POST /api/reschedule-requests/{id}/approve` | the lesson's teacher |
| `POST /api/reschedule-requests/{id}/reject` `{"note?"}` | the lesson's teacher |

```bash
STUDENT=$(curl -s localhost:8080/api/auth/login -H 'X-Tenant: demo' -H 'Content-Type: application/json' \
  -d '{"email":"maria@demo.com","password":"maria-temp-1"}' | jq -r .token)

curl -s localhost:8080/api/lessons/1/reschedule-requests -H 'X-Tenant: demo' -H "Authorization: Bearer $STUDENT" \
  -H 'Content-Type: application/json' -d '{"proposedStartsAt":"2030-01-16T13:00:00Z","reason":"Doctor appointment"}'

curl -s -X POST localhost:8080/api/reschedule-requests/1/approve -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER"
```

Response fields: `id`, `lessonId`, `lessonTopic`, `lessonStatus`, `teacherId`, `teacherName`, `studentId`,
`studentName`, `lessonStartsAt` (current), `durationMinutes`, `originalStartsAt` (when requested),
`proposedStartsAt`, `reason`, `status`, `decisionNote`, `decidedByName`, `decidedAt`, `createdAt`.

### Tasks and assignments

Teachers keep a library of **tasks** (`title` up to 200, `instructions` in Markdown up to 10000 characters) and
give them to their students as **assignments** with an optional due date. Tasks are private to their teacher
(others get 404); students and admins cannot use `/api/tasks` (403).

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/tasks`, `PUT /api/tasks/{id}` | teacher | `{"title", "instructions"}` |
| `GET /api/tasks` | teacher | newest first; `q` (title search), `includeArchived=true` |
| `GET /api/tasks/{id}` | teacher | |
| `POST /api/tasks/{id}/archive`, `/unarchive` | teacher | archived tasks are hidden by default and cannot be assigned (409) |
| `DELETE /api/tasks/{id}` | teacher | only tasks without assignments (otherwise 409: archive it) |
| `POST /api/tasks/{id}/assignments` | teacher | `{"studentIds": [..], "dueAt?"}`: one `ASSIGNED` assignment per student, all or nothing; students must be the teacher's active students (400), `dueAt` in the future (400); a student who still has the task `ASSIGNED` → 409 |
| `DELETE /api/assignments/{id}` | the task's teacher | only while `ASSIGNED` (409 once submitted) |
| `GET /api/assignments` | all | students: theirs; teachers: of their tasks; admins: all. Filters `status` (repeatable), `studentId`, `taskId`; soonest due first, no due date last |
| `GET /api/assignments/{id}` | all | 404 when the caller cannot see it |

```bash
curl -s localhost:8080/api/tasks -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"title":"Irregular verbs","instructions":"Write **10** sentences."}'

curl -s localhost:8080/api/tasks/1/assignments -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"studentIds":[1],"dueAt":"2030-01-20T21:00:00Z"}'

curl -s "localhost:8080/api/assignments?status=ASSIGNED" -H 'X-Tenant: demo' -H "Authorization: Bearer $STUDENT"
```

Task fields: `id`, `title`, `instructions`, `archived`, `assignmentCount`, `createdAt`, `updatedAt`.
Assignment fields: `id`, `taskId`, `taskTitle`, `instructions`, `teacherId`, `teacherName`, `studentId`,
`studentName`, `dueAt`, `assignedAt`, `status` (`ASSIGNED`, `SUBMITTED`, `GRADED`), `overdue` (due date
passed and still `ASSIGNED`).

### Creating a school (platform operator)

```bash
curl -s localhost:8080/api/platform/tenants -H 'X-Platform-Key: dev-platform-key' \
  -H 'Content-Type: application/json' -d '{
    "slug": "alpha", "name": "Alpha School", "timeZone": "America/Sao_Paulo",
    "admin": {"name": "Alice", "email": "alice@alpha.com", "password": "change-me-1"}
  }'
```

The first admin must change the password on first login.

### Configuration

| Variable | Purpose |
|---|---|
| `EDUCARE_JWT_SECRET` | HMAC secret for tokens, at least 32 bytes. Required outside `dev`/`test`. |
| `EDUCARE_PLATFORM_KEY` | Key for `/api/platform/**`. Required outside `dev`/`test` (dev default: `dev-platform-key`). |

## Testing

```bash
./gradlew test
```

Tests run against a real PostgreSQL started by Testcontainers (one container per test run), so Docker
must be running. Test classes extend `support/IntegrationTest`, which provides the container, a clean database per test,
MockMvc with Spring Security, and helpers to create schools, teachers and tokens.

## Database migrations

The schema is managed only by [Flyway](https://documentation.red-gate.com/flyway) migrations in
`src/main/resources/db/migration`. Hibernate never creates tables; it validates the entities against
the schema at startup (`ddl-auto: validate`), so a missing migration fails fast.

- Name files `V<number>__<description>.sql`, e.g. `V2__create_tenants_and_users.sql`.
- Never edit a migration that has already been applied; add a new one instead.
- Use `TIMESTAMPTZ` for instants and match column lengths to the validation limits of the DTOs.

## Planning

Planned changes, in implementation order, are in `openspec/changes/` (OpenSpec).
