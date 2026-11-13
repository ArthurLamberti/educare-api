# Design

## Context

See proposal.md - Why. Spring Boot 4.1 with Spring Data JPA; the only table is `students` (id identity, name, email unique, birth_date, created_at). No production data exists yet.

## Goals / Non-Goals

**Goals:**
- One schema definition (Flyway migrations) used identically in dev, test and prod.
- `./gradlew test` and `./gradlew bootRun` work on a machine with Docker and nothing else installed.

**Non-Goals:**
- Database backups, pooling tuning or hosting choices.
- Seed data (comes with the tenants change, which needs it).

## Decisions

- **Flyway over Liquibase**: plain SQL migrations are easier to review and match how the team writes schema. Migrations live in `db/migration`, named `V<n>__<description>.sql`; applied migrations are never edited.
- **Docker Compose for dev** (`compose.yaml`, `postgres:17`, db/user/password `educare`, port 5432, named volume). Spring Boot's Docker Compose support (`spring-boot-docker-compose`) can start it automatically on `bootRun`; we add it as a `developmentOnly` dependency so `bootRun` just works, while the `dev` profile still holds explicit connection settings for running without it.
- **Testcontainers for tests**: a `@TestConfiguration` with a `@ServiceConnection PostgreSQLContainer` shared by all `@SpringBootTest` classes, so tests exercise the same SQL dialect and constraints as production (H2 accepted things PostgreSQL rejects). Alternative considered: H2 in PostgreSQL mode - rejected because it diverges on identity columns, `timestamptz` and constraint errors.
- **`ddl-auto: validate` everywhere**: Hibernate checks entity mappings against the migrated schema at startup, catching drift early.
- **Column types**: `created_at timestamptz`, `birth_date date`, `email varchar(255)`, `name varchar(150)` matching the validation limits.

## Risks / Trade-offs

- [Docker becomes a hard requirement for tests] → Document it in the README; CI runners with Docker support Testcontainers out of the box.
- [Tests get slower because a container starts] → One shared container per test JVM (singleton), reused across test classes.
- [The `students` table will be reshaped by the tenants/accounts changes] → Acceptable: those changes add new migrations; there is no production data to preserve.

## Migration Plan

Developers run `docker compose up -d` (or rely on Compose support) then `./gradlew bootRun`. Rollback is reverting the commit; there is no deployed database yet.
