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

## Testing

```bash
./gradlew test
```

Tests run against a real PostgreSQL started by Testcontainers (one container per test run), so Docker
must be running. Test classes annotated with `@SpringBootTest` must also
`@Import(TestcontainersConfiguration::class)`.

## Database migrations

The schema is managed only by [Flyway](https://documentation.red-gate.com/flyway) migrations in
`src/main/resources/db/migration`. Hibernate never creates tables; it validates the entities against
the schema at startup (`ddl-auto: validate`), so a missing migration fails fast.

- Name files `V<number>__<description>.sql`, e.g. `V2__create_tenants_and_users.sql`.
- Never edit a migration that has already been applied; add a new one instead.
- Use `TIMESTAMPTZ` for instants and match column lengths to the validation limits of the DTOs.

## Planning

Planned changes, in implementation order, are in `openspec/changes/` (OpenSpec).
