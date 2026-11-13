# Proposal

## Why

The product requires PostgreSQL, but development and tests run on in-memory H2 with `create-drop`, and the `prod` profile uses `ddl-auto: validate` with nothing that creates the schema, so the API fails to start against a fresh database. Every later change (tenants, lessons, assignments, flashcards) adds tables, so a versioned schema must exist before them.

## What Changes

- Run PostgreSQL locally through Docker Compose and use it in the `dev` profile instead of H2.
- Add Flyway; the schema is created only by versioned migrations. `V1` creates the current `students` table.
- Hibernate validates the schema (`ddl-auto: validate`) in every profile; nothing generates DDL anymore.
- Tests run against a real PostgreSQL through Testcontainers instead of H2.
- Remove the H2 dependency and the H2 console.
- Document how to start the database and the API in a README.

No API behavior changes.

## Capabilities

### New Capabilities
None. Infrastructure only (`skip_specs: true`).

### Modified Capabilities
None.

## Impact

- `build.gradle.kts`: add Flyway (with the PostgreSQL module) and Testcontainers PostgreSQL; remove H2 and `spring-boot-h2console`.
- `application.yml`: `dev` points to the Compose database; all profiles use `validate`.
- New `compose.yaml`, `src/main/resources/db/migration/V1__create_students.sql`, `README.md`.
- Developers need Docker to run the API and the tests.
