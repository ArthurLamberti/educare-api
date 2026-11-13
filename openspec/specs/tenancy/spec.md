# tenancy Specification

## Purpose
Isolates each school's data: provisions schools (tenants) and resolves the tenant of every request so users only ever see and change data of their own school.

## Requirements

### Requirement: Tenant provisioning
The system SHALL let the platform operator create a tenant with a slug (lowercase letters, digits and hyphens, 3-40 chars, unique, not in the reserved list), a display name, an IANA time zone, and a first admin user (name, email, password), through `POST /api/platform/tenants`, authorized only by the platform API key in the `X-Platform-Key` header. The created tenant SHALL have kind `SCHOOL`.

#### Scenario: Valid provisioning
- **WHEN** the operator posts a new slug, name, time zone and admin data with the correct platform key
- **THEN** the system responds 201 with the tenant, and the admin can log in on that tenant

#### Scenario: Wrong or missing platform key
- **WHEN** the request has no `X-Platform-Key` or a wrong one
- **THEN** the system responds 401 and creates nothing

#### Scenario: Slug already taken
- **WHEN** the slug already exists
- **THEN** the system responds 409

#### Scenario: Invalid slug or time zone
- **WHEN** the slug has uppercase letters, spaces or fewer than 3 chars, is reserved (e.g. `www`), or the time zone is not a valid IANA zone
- **THEN** the system responds 400 with an `errors` entry per invalid field

### Requirement: Tenant resolution per request
The system SHALL resolve the tenant of every request from the `X-Tenant` header (the tenant slug), except platform (`/api/platform/**`) and public sign-up (`/api/public/**`) endpoints. A missing header or unknown slug SHALL be rejected with 404 and a ProblemDetail before any other processing.

#### Scenario: Unknown tenant
- **WHEN** a request is sent with `X-Tenant: no-such-school`
- **THEN** the system responds 404

#### Scenario: Missing tenant
- **WHEN** a request to `/api/auth/login` has no `X-Tenant` header
- **THEN** the system responds 404

#### Scenario: Public endpoint
- **WHEN** a request to `/api/public/slug-availability` has no `X-Tenant` header
- **THEN** the system processes it normally

### Requirement: Tenant isolation
The system SHALL only read and write data belonging to the resolved tenant. An access token issued for one tenant SHALL be rejected with 401 when used with a different `X-Tenant`. Resources of another tenant SHALL be reported as 404, never revealing they exist.

#### Scenario: Token from another school
- **WHEN** a user of tenant `alpha` sends their token with `X-Tenant: beta`
- **THEN** the system responds 401

#### Scenario: Resource of another school
- **WHEN** an admin of `alpha` requests a teacher id that belongs to `beta`
- **THEN** the system responds 404

#### Scenario: Same email in two schools
- **WHEN** a user with email `ana@x.com` exists in `alpha` and an admin of `beta` creates a teacher with `ana@x.com`
- **THEN** the system creates it; the two accounts are independent

### Requirement: Tenant information for the current user
The system SHALL include the tenant slug, name and time zone in the current user's data, so clients can render dates in the school's time zone.

#### Scenario: Current user includes tenant
- **WHEN** an authenticated user requests `GET /api/auth/me`
- **THEN** the response includes `tenant.slug`, `tenant.name` and `tenant.timeZone`

### Requirement: Public school information
The system SHALL return the resolved tenant's slug, name, time zone and kind through `GET /api/tenant` without authentication, so clients can show the school or space before login. Unknown tenants SHALL get 404 like any other request.

#### Scenario: Known school
- **WHEN** a client requests `GET /api/tenant` with `X-Tenant: alpha` and no token
- **THEN** the system responds 200 with `slug`, `name`, `timeZone` and `kind`

#### Scenario: Unknown school
- **WHEN** a client requests `GET /api/tenant` with `X-Tenant: nope`
- **THEN** the system responds 404

### Requirement: Tenant kind
Each tenant SHALL have a kind: `SCHOOL` (provisioned by the operator, with admins and teachers) or `INDIVIDUAL` (created by a private teacher's sign-up, with that teacher as the only staff user and no admin). Tenants provisioned by the operator SHALL be `SCHOOL`.

#### Scenario: Provisioned school
- **WHEN** the operator provisions a tenant
- **THEN** its kind is `SCHOOL`

#### Scenario: Private teacher's space
- **WHEN** a private teacher signs up
- **THEN** the created tenant's kind is `INDIVIDUAL`

### Requirement: Space settings
The system SHALL let the name and time zone of the resolved tenant be changed through `PUT /api/tenant` by an `ADMIN` of a `SCHOOL`, or by the `TEACHER` of an `INDIVIDUAL` tenant. Other users SHALL get 403. The slug SHALL NOT change.

#### Scenario: Private teacher renames the space
- **WHEN** the teacher of an individual space sends a new name and time zone
- **THEN** the system responds 200 and later responses use the new values

#### Scenario: School teacher
- **WHEN** a teacher of a `SCHOOL` tenant calls `PUT /api/tenant`
- **THEN** the system responds 403
