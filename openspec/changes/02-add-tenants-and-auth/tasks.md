# Tasks

## 1. Schema and tenants

- [ ] 1.1 Add migration `V2__create_tenants_and_users.sql` (tables, composite unique email, role check) and verify Flyway applies it and Hibernate validation passes on startup
- [ ] 1.2 Create `Tenant` entity/repository and `TenantOwnedEntity` mapped superclass with `@TenantId`, plus a `CurrentTenantIdentifierResolver`; verify with a repository test that an entity saved under tenant A is not found under tenant B
- [ ] 1.3 Add the `X-Tenant` resolution filter (404 ProblemDetail for missing/unknown slug, skipped for `/api/platform/**`) and verify with MockMvc tests for unknown and missing tenant

## 2. Security and login

- [ ] 2.1 Add Spring Security + OAuth2 resource server dependencies, JWT encoder/decoder from `EDUCARE_JWT_SECRET`, stateless config, ProblemDetail entry point and access-denied handler; verify `GET /api/students` without token returns 401 ProblemDetail
- [ ] 2.2 Implement `User` entity/repository (tenant-owned), `BCryptPasswordEncoder`, and `POST /api/auth/login`; verify tests for valid login, wrong password, unknown email, deactivated user and wrong tenant all behave per spec
- [ ] 2.3 Add the post-authentication check that token `tid` matches the resolved tenant and the user is still active; verify tests for cross-tenant token (401) and deactivated-after-login (401)
- [ ] 2.4 Implement `GET /api/auth/me` (with tenant slug/name/timeZone) and `POST /api/auth/change-password`; verify tests for success, wrong current password and short new password

## 3. Platform provisioning

- [ ] 3.1 Implement `POST /api/platform/tenants` with `X-Platform-Key` check, slug/time-zone validation, and first admin creation; verify tests for success (admin can then log in), wrong key (401), duplicate slug (409) and invalid slug/zone (400)
- [ ] 3.2 Add the `dev`-only seed of tenant `demo` with `admin@demo.com` and verify `bootRun` logs the seed once and login works

## 4. Teacher management

- [ ] 4.1 Implement `/api/teachers` (create, list, get, update, activate, deactivate) restricted to `ADMIN`; verify tests for each scenario in the teacher-management spec, including 403 for teachers and 404 for another tenant's teacher

## 5. Protect existing endpoints and document

- [ ] 5.1 Restrict `/api/students/**` to `ADMIN`/`TEACHER`, update `StudentControllerTest` to authenticate, and verify the full test suite passes
- [ ] 5.2 Document in `README.md` the auth flow, required headers (`X-Tenant`, `Authorization`), environment variables and how to provision a tenant with curl; verify the curl example works against `bootRun`
