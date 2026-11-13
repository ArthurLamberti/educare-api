# Educare API

REST API for Educare (Kotlin, Spring Boot 4, PostgreSQL). The web frontend lives in `../educare-web`.

## Requirements

- JDK 21
- Docker (PostgreSQL for development and for tests)

## Running

```bash
./gradlew bootRun
```

`bootRun` starts the PostgreSQL and Mailpit services from `compose.yaml` automatically (Spring Boot Docker Compose
support), stops it again when the API shuts down, and applies the database migrations. If the
container is already running it is reused and left running. The API listens on http://localhost:8080.

To manage the database yourself:

```bash
docker compose up -d      # start PostgreSQL (localhost:5432, db/user/password: educare)
docker compose down       # stop it (data is kept in the educare-postgres volume)
docker compose down -v    # stop it and delete all data
```

Connection settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

Emails sent in development (sign-up confirmation) are caught by Mailpit: open http://localhost:8026.
Profiles: `dev` (default) and `prod` (`SPRING_PROFILES_ACTIVE=prod`).

## Schools, login and roles

Educare is multi-tenant: each school or private teacher's space (tenant) has a slug that matches its web address
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
`durationMinutes`, `topic`, `notes`, `status`, `seriesId` (null unless created by a series), `createdAt`, `updatedAt`.

### Weekly lessons (series)

A teacher can repeat a lesson every week on one or more weekdays, with **no end date**. The series
holds the school-local time (`startTime`), so lessons stay at the same wall-clock time across DST changes.
Lessons are created for the next **8 weeks**; a job (every 6 hours, not in the `test` profile) keeps
that horizon filled, skipping slots that became busy, and ends series of inactive students.

| Endpoint | Who | Notes |
|---|---|---|
| `POST /api/lesson-series` | teacher | `studentId`, `weekdays` (e.g. `["TUESDAY","THURSDAY"]`), `startsOn` (school date, not in the past), `startTime` (`HH:mm`, school time), `durationMinutes` (15-240), `topic`, `notes?`. All or nothing: if any lesson clashes → 409 listing the busy times. Answers `series` and `lessonsCreated` |
| `GET /api/lesson-series` | all | active series: teachers theirs, students theirs, admins all; `nextLessonAt` |
| `POST /api/lesson-series/{id}/end` `{"from?"}` | the series' teacher | cancels its scheduled lessons from `from` (school date, default today) and stops it; already ended → 409. Answers `series` and `lessonsCanceled` |

Lessons of a series are ordinary lessons: they can be edited, canceled or moved one by one.

```bash
curl -s localhost:8080/api/lesson-series -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' \
  -d '{"studentId":1,"weekdays":["TUESDAY","THURSDAY"],"startsOn":"2030-01-15","startTime":"14:00","durationMinutes":60,"topic":"Conversation"}'
```

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

### Submissions and grading

The assigned student answers with text (Markdown, up to 20000 characters); the task's teacher grades
with a `score` from 0 to 10 (at most one decimal) and `feedback`.

```
ASSIGNED ──submit──▶ SUBMITTED ──grade──▶ GRADED
                     ◀─┘ edit answer      ◀─┘ revise grade
```

| Endpoint | Who | Notes |
|---|---|---|
| `PUT /api/assignments/{id}/submission` `{"answer"}` | the assigned student | first submit snapshots the task instructions and records `submittedAt`; edits keep it; after the due date `late: true` (accepted); `GRADED` → 409 |
| `PUT /api/assignments/{id}/grade` `{"score", "feedback", "version?"}` | the task's teacher | `ASSIGNED` → 409; invalid score → 400 `errors.score`; send the submission `version` you reviewed: if the student changed the answer since, 409 |
| `GET /api/assignments?status=SUBMITTED` | teacher | grading queue, oldest submission first |

Both answer with the assignment, which now also carries `submission` (`answer`, `instructionsSnapshot`,
`submittedAt`, `updatedAt`, `late`, `version`) once submitted and `grade` (`score`, `feedback`, `gradedAt`,
`gradedByName`) once graded.

```bash
curl -s -X PUT localhost:8080/api/assignments/1/submission -H 'X-Tenant: demo' -H "Authorization: Bearer $STUDENT" \
  -H 'Content-Type: application/json' -d '{"answer":"I went to the park. I saw a friend."}'

curl -s -X PUT localhost:8080/api/assignments/1/grade -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"score":8.5,"feedback":"Good! Watch the irregular verbs.","version":0}'
```

### Flashcards

Each student has **one list of words** (`front`/`back`, up to 1000 characters each, at most 2000 cards per
student). Only the student's teacher adds, edits or deletes them; the student reviews them.

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/students/{studentId}/cards` | the student, their teacher, admins | oldest first |
| `POST /api/students/{studentId}/cards` | the student's teacher | `{"front", "back"}`; 2000 cards → 409 |
| `POST /api/students/{studentId}/cards/batch` | the student's teacher | `{"cards": [{"front", "back"}, ...]}` (1 to 500), all or nothing: an invalid item → 400 with `errors["cards[i].front"]`, going past 2000 cards → 409 |
| `GET /api/cards` | student | their own cards |
| `PUT /api/cards/{id}`, `DELETE /api/cards/{id}` | the student's teacher | edits keep the review state |
| `GET /api/flashcards/progress` | teacher, admin | per student (teacher: their active students): `cards`, `newCards`, `learning`, `mature`, `dueToday`, `newToday`, `reviewsLast7Days` |

```bash
curl -s localhost:8080/api/students/1/cards/batch -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER" \
  -H 'Content-Type: application/json' -d '{"cards":[{"front":"cat","back":"gato"},{"front":"dog","back":"cachorro"}]}'
```

### Flashcard review (spaced repetition)

The review state lives on the card (a card never reviewed is **new**). "Today" is the school's day in its time zone.

| Endpoint | Who | Notes |
|---|---|---|
| `GET /api/reviews/queue` | student | due cards (oldest due first), then new cards in the order added: at most **20 new cards per school day**; at most 200 cards per response. `dueCount`, `newCount`, `cards[]` (`cardId`, `front`, `back`, `isNew`, `dueAt`), `nextDueAt` when nothing is due |
| `POST /api/reviews` `{"cardId", "rating"}` | student | `rating`: `AGAIN`, `HARD`, `GOOD`, `EASY`; answers the new `dueAt`, `intervalDays`, `ease`, `repetitions`. Another student's card → 404 |
| `GET /api/reviews/progress` | student | their counters, same fields as `/api/flashcards/progress` |

Scheduling (SM-2 variant, `review/Scheduler.kt`). Cards start with ease 2.5, interval 0, 0 repetitions;
ease never drops below 1.3; intervals multiply by the ease before the rating:

| Rating | Ease | Interval (days) | Repetitions |
|---|---|---|---|
| `AGAIN` | −0.20 | 0, due again in 10 minutes | 0 |
| `HARD` | −0.15 | 1 if repetitions was 0, else max(1, round(interval × 1.2)) | +1 |
| `GOOD` | = | 1 on the first success, 6 on the second, else round(interval × ease) | +1 |
| `EASY` | +0.15 | 4 on the first success, else round(interval × ease × 1.3) | +1 |

Intervals of a day or more are due at the **start of that day** in the school's time zone. Learning means
interval under 21 days, mature 21 or more. Deleting a card deletes its review history.

```bash
curl -s localhost:8080/api/reviews/queue -H 'X-Tenant: demo' -H "Authorization: Bearer $STUDENT"

curl -s localhost:8080/api/reviews -H 'X-Tenant: demo' -H "Authorization: Bearer $STUDENT" \
  -H 'Content-Type: application/json' -d '{"cardId":1,"rating":"GOOD"}'

curl -s localhost:8080/api/flashcards/progress -H 'X-Tenant: demo' -H "Authorization: Bearer $TEACHER"
```

### Creating a school (platform operator)

```bash
curl -s localhost:8080/api/platform/tenants -H 'X-Platform-Key: dev-platform-key' \
  -H 'Content-Type: application/json' -d '{
    "slug": "alpha", "name": "Alpha School", "timeZone": "America/Sao_Paulo",
    "admin": {"name": "Alice", "email": "alice@alpha.com", "password": "change-me-1"}
  }'
```

The first admin must change the password on first login. Reserved addresses (`www`, `api`, `admin`,
… see `tenant/Slugs.kt`) are refused with 400.

### Private teachers (sign-up)

A tenant has a `kind`: `SCHOOL` (provisioned by the operator, with admins and teachers) or `INDIVIDUAL`
(a private teacher's own space, created by a public sign-up). In an individual space the teacher is
an ordinary `TEACHER` and there is no admin; everything a teacher does works the same.

The public endpoints need no `X-Tenant` and no token:

| Endpoint | Notes |
|---|---|
| `GET /api/public/slug-availability?slug=` | `available`, and `reason` when not: `INVALID`, `RESERVED`, `TAKEN` |
| `POST /api/public/signups` | `name`, `email`, `password` (8+), `spaceName`, `slug`, `timeZone` → 202 `{slug}`; creates the space and an unconfirmed teacher and emails a confirmation link. Taken address → 409, reserved → 400 `errors.slug` |
| `POST /api/public/signups/confirm` `{"token"}` | 200 `{slug}`; the link is single use and valid 48 hours, otherwise 400 |
| `POST /api/public/signups/resend` `{"email", "slug"}` | always 202; sends a new link (older ones stop working) only if an unconfirmed account matches |

- Signing up again for a still-unconfirmed address with the same password corrects that sign-up (name,
  email, space name, time zone) and sends a new link, so a mistyped email does not hold the address.
- Until the email is confirmed, login with the right password answers 403 with `code: EMAIL_NOT_CONFIRMED`.
- Spaces never confirmed are deleted after 7 days (hourly job), and the address becomes free again.
- Rate limits (in memory, per instance), 429 with `Retry-After` when exceeded: sign-up 5/hour per client
  IP and 3/day per email, address checks 60/minute per IP, confirmations 20/hour per IP, resends 10/hour
  per IP and 3/hour per account. The client IP comes from `X-Forwarded-For` only when the request comes
  from `educare.security.trusted-proxies` (default: loopback, where educare-web runs in development).
- The confirmation link points at `<scheme>://<slug>.<EDUCARE_WEB_ROOT_DOMAIN>/confirm?token=…`.

```bash
curl -s localhost:8080/api/public/signups -H 'Content-Type: application/json' -d '{
    "name": "Ana Souza", "email": "ana@example.com", "password": "password123",
    "spaceName": "Ana – Inglês", "slug": "ana-ingles", "timeZone": "America/Sao_Paulo"
  }'
# open the link from the email in Mailpit (http://localhost:8026), or post its token:
curl -s localhost:8080/api/public/signups/confirm -H 'Content-Type: application/json' -d '{"token":"..."}'
```

### Space activation

Every tenant has a `status`: `PENDING`, `ACTIVE` or `SUSPENDED`. Schools start `ACTIVE`; private spaces
start `PENDING` and are only usable once activated, today by the operator, later by a confirmed payment
(`TenantActivationService.activate(id, ActivationSource.PAYMENT)` from the gateway's webhook).

- In a space that is not `ACTIVE`, users can log in, call `/api/auth/**` and `GET /api/tenant`; anything
  else answers 403 with `code: ACCOUNT_INACTIVE` and `status`. The status is checked on every request.
- When the teacher confirms the email, the operator gets an email (`EDUCARE_MAIL_OPERATOR`; in dev
  `operador@educare.localhost`, visible in Mailpit). On activation the teacher gets "Seu espaço na
  Educare está liberado".
- Activation is refused (422) while the teacher has not confirmed the email.
- Wrong platform keys: 10 per client IP in 15 minutes, then 429.

```bash
K='X-Platform-Key: dev-platform-key'
curl -s -H "$K" 'localhost:8080/api/platform/tenants?status=PENDING'   # with owner name/email/emailConfirmed
curl -s -H "$K" -X POST localhost:8080/api/platform/tenants/{id}/activate
curl -s -H "$K" -X POST localhost:8080/api/platform/tenants/{id}/suspend
```

The web has a panel for this at `http://localhost:3000/admin`, used by platform admins.

### Platform admin

At startup the API creates the system admin `EDUCARE_ADMIN_EMAIL` (default `arthurlamberti001@gmail.com`)
if it does not exist, with a random password. To set it, use "Esqueci a senha (ou primeiro acesso)" on
`localhost:3000/admin/login`; in development the email lands in Mailpit (http://localhost:8026).

| Endpoint | Notes |
|---|---|
| `POST /api/platform/auth/login` `{"email","password"}` | `{token, expiresAt, admin}`; 10 attempts per IP in 15 minutes |
| `GET /api/platform/auth/me` | the admin of the token |
| `POST /api/platform/auth/forgot-password` `{"email"}` | always 202 |
| `POST /api/platform/auth/reset-password` `{"token","newPassword"}` | 204, or 400 for a bad link |

`/api/platform/**` accepts `Authorization: Bearer <admin token>` or `X-Platform-Key` (scripts). Admin
tokens are rejected on school endpoints, and school tokens on platform endpoints.

### Forgot password

`POST /api/auth/forgot-password` `{"email"}` (with `X-Tenant`) always answers 202 and emails a link to
`<slug>.<root>/reset-password?token=…` when an active, confirmed account exists. `POST
/api/auth/reset-password` `{"token","newPassword"}` → 204; it also clears a temporary password. Links
are single use, valid 1 hour, replaced by newer ones, and only work in their own school. At most 3
emails per account per hour; asks 10/hour and resets 20/hour per IP. Sessions opened before a reset
stay valid until they expire.

### Settings

`PUT /api/tenant` `{"name", "timeZone"}` changes the school's or space's name and time zone (the slug
never changes). Allowed to a school's admins and to the teacher of an individual space; others get 403.
`GET /api/tenant` and `GET /api/auth/me` include `kind` and `status`.

### Configuration

| Variable | Purpose |
|---|---|
| `EDUCARE_JWT_SECRET` | HMAC secret for tokens, at least 32 bytes. Required outside `dev`/`test`. |
| `EDUCARE_PLATFORM_KEY` | Key for `/api/platform/**`. Required outside `dev`/`test` (dev default: `dev-platform-key`). |
| `EDUCARE_WEB_ROOT_DOMAIN`, `EDUCARE_WEB_SCHEME` | Where educare-web runs, for links in emails (default `localhost:3000`, `http`) |
| `EDUCARE_MAIL_FROM` | Sender of emails |
| `EDUCARE_ADMIN_EMAIL`, `EDUCARE_ADMIN_NAME` | System admin created at startup if missing (default `arthurlamberti001@gmail.com`, "Administrador") |
| `EDUCARE_MAIL_OPERATOR` | Who is told that a new space waits for activation (empty: nobody; dev: `operador@educare.localhost`) |
| `MAIL_HOST`, `MAIL_PORT` | SMTP in `dev` (default Mailpit, `localhost:1026`); in `prod` set `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Without an SMTP host, emails are only logged |

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
