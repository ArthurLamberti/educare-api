# Tasks

## 1. Database setup

- [x] 1.1 Add `compose.yaml` with a `postgres:17` service (db/user/password `educare`, port 5432, named volume) and verify `docker compose up -d` starts a healthy container
- [x] 1.2 Update `build.gradle.kts`: add Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`), `spring-boot-docker-compose` as `developmentOnly`, Testcontainers PostgreSQL for tests; remove H2 and `spring-boot-h2console`; verify `./gradlew dependencies` resolves

## 2. Migrations and configuration

- [x] 2.1 Create `src/main/resources/db/migration/V1__create_students.sql` matching the `Student` entity and verify Flyway applies it on startup (`flyway_schema_history` has version 1)
- [x] 2.2 Point the `dev` profile to the Compose database, set `ddl-auto: validate` in all profiles, remove H2 console settings, and verify `./gradlew bootRun` starts and `GET /api/students` responds 200

## 3. Tests

- [x] 3.1 Add a shared Testcontainers `@ServiceConnection` PostgreSQL configuration used by all `@SpringBootTest` classes and verify `./gradlew test` passes against PostgreSQL

## 4. Documentation

- [x] 4.1 Write `README.md` (requirements: JDK 21 + Docker; how to start the database, run, and test; migration naming rules) and verify the documented commands work from a clean checkout
