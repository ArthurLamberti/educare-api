# Proposal

## Why

Educare serves several schools, and each school's data must be invisible to the others. Today the API is anonymous and global: anyone can read and change every student. Login, roles (admin, teacher, student) and tenant isolation are prerequisites for every feature that follows (lessons, assignments, flashcards), since all of them depend on knowing who the user is and which school they belong to.

## What Changes

- New **tenants** (schools) identified by a unique slug that matches the subdomain (`<slug>.educare.com`). Every request names its tenant in the `X-Tenant` header; the web app derives it from the subdomain.
- New **users** belonging to one tenant, with role `ADMIN`, `TEACHER` or `STUDENT`, unique email per tenant, BCrypt password, active flag and a "must change password" flag.
- **Login** with email + password returns a signed access token (JWT) carrying user id, tenant and role; every other endpoint requires it. Endpoints to read the current user and to change the password.
- **Tenant provisioning** endpoint for the platform operator, protected by a platform API key, that creates a school and its first admin.
- **Teacher management** for admins: create, list, update and deactivate teachers of their school.
- **BREAKING**: all `/api/**` endpoints except login and platform provisioning now require authentication; `/api/students` requires an `ADMIN` or `TEACHER` token until the student accounts change refines it.

## Capabilities

### New Capabilities
- `tenancy`: tenant (school) provisioning and per-request tenant resolution and isolation.
- `authentication`: login, access tokens, current user, password change, roles.
- `teacher-management`: admin CRUD of teacher accounts within a school.

### Modified Capabilities
None at spec level here; `student-management` is reshaped in change 03.

## Impact

- New packages `tenant`, `auth`, `user`, `teacher`; Spring Security added.
- Migration `V2` adds `tenants` and `users`; tenant-owned tables get a `tenant_id` column from now on.
- New configuration: JWT secret, token lifetime, platform API key.
- educare-web change `02-add-tenants-and-auth` consumes these endpoints.
