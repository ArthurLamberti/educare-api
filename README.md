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
