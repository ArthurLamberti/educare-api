# Design

## Context

After change 01 the API runs on PostgreSQL with Flyway. There is no authentication; `students` has no owner. The web app calls the API only from the Next.js server, so the API never receives browser requests directly (no CORS, no cookies on the API side).

## Goals / Non-Goals

**Goals:**
- One database, shared schema, row-level tenant isolation that cannot be forgotten by a single missing `WHERE`.
- Stateless API authentication that the Next.js server can carry per request.

**Non-Goals:**
- Refresh tokens, "remember me", password reset by email, SSO, rate limiting of login attempts (later changes).
- A platform admin UI; provisioning is an API call for the operator.

## Decisions

### Tenant identification: `X-Tenant` header + `tenant_id` column
The web app maps `<slug>.educare.com` to the slug and sends `X-Tenant: <slug>` on every call. A filter resolves the slug to a tenant (cached in memory, 404 if unknown). It is the first filter of the Spring Security chain (before `SecurityContextHolderFilter`) rather than a separate servlet filter, so MockMvc tests with `springSecurity()` exercise it too; the platform-key check is a sibling filter in the same position. Tables owned by a tenant carry `tenant_id bigint not null references tenants`. The API does not parse the `Host` itself because it is never called by browsers.

### Isolation via Hibernate `@TenantId`
Tenant-owned entities extend a `TenantOwnedEntity` mapped superclass with a `@TenantId tenantId` field, and a `CurrentTenantIdentifierResolver` returns the tenant resolved for the request. Hibernate then filters every query and stamps every insert automatically. Alternative considered: explicit `findByTenantId...` repository methods - rejected because one forgotten method leaks data across schools. Native queries must add the filter by hand; the code review checklist and a test per repository guard this.
`tenants` itself and the platform endpoint are not tenant-owned.

### Uniqueness per tenant
Unique constraints become composite: `users (tenant_id, email)`. Emails are normalized to lowercase on write (with a `CHECK (email = lower(email))` constraint), which makes the plain unique constraint case-insensitive and keeps lookups index-friendly. Login looks the user up by tenant + email.

### Authentication: JWT (HS256) via Spring Security resource server
`spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`, with a `NimbusJwtEncoder/Decoder` on a symmetric secret (`EDUCARE_JWT_SECRET`, at least 32 bytes). Claims: `sub` (user id), `tid` (tenant id), `role`, `iat`, `exp` (12h). Stateless sessions, CSRF disabled (no cookies on the API).
Alternatives: opaque server-side sessions (needs a session store and a lookup on every request); asymmetric keys (useful only when other services verify tokens - not the case).
A filter after authentication checks that `tid` equals the resolved tenant (else 401) and that the user is still active (cached lookup with short TTL, ~30 s), so deactivation takes effect without waiting for token expiry.

### Authorization
`@EnableMethodSecurity` and `@PreAuthorize("hasRole('ADMIN')")` etc. on controllers. A `CurrentUser` helper exposes id/tenant/role to services. Ownership checks (a teacher only sees their own students, etc.) live in services and answer 404 for others' resources.

### Error responses
401/403 produced by Spring Security are rendered as ProblemDetail through a custom `AuthenticationEntryPoint` and `AccessDeniedHandler`, keeping one error format for the web app.

### Passwords
`BCryptPasswordEncoder` (strength 10). Login compares against a dummy hash when the user does not exist to keep timing similar.

### Platform provisioning
`POST /api/platform/tenants` is permitted without JWT but requires `X-Platform-Key` equal to `EDUCARE_PLATFORM_KEY` (constant-time comparison). It creates the tenant and its first admin with `mustChangePassword = true`. The `X-Tenant` filter skips `/api/platform/**`.

### Data model (migration V2)
- `tenants(id, slug unique, name, time_zone, created_at)`
- `users(id, tenant_id, name, email, password_hash, role, must_change_password, active, created_at)`, unique `(tenant_id, lower(email))`, check on `role`.
Teachers and admins are just users; no separate table.

### Development seed
A `dev`-profile-only `ApplicationRunner` provisions tenant `demo` with `admin@demo.com / password123` if it does not exist, so `demo.localhost:3000` works right after `bootRun`.

## Risks / Trade-offs

- [`@TenantId` does not cover native SQL] → Avoid native queries; when unavoidable, add the tenant predicate and a cross-tenant test.
- [JWT cannot be revoked before expiry] → Active-user check on each request (cached ~30 s) closes the gap for deactivation; 12h lifetime limits exposure.
- [Shared secret leak lets anyone mint tokens] → Secret only in environment variables, never in the repo; startup fails if it is missing or short outside `dev`/tests.
- [Existing `/api/students` clients break] → Only client is educare-web, updated in its change 02.

## Migration Plan

Deploy V2; set `EDUCARE_JWT_SECRET` and `EDUCARE_PLATFORM_KEY`; provision the first schools through the platform endpoint. Existing `students` rows (none in production) are handled by change 03.
