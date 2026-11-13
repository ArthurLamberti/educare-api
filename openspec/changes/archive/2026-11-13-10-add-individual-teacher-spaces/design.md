# Design

## Context

Tenancy (change 02) isolates everything by `tenant_id`; a tenant is created only by `POST /api/platform/tenants` with an `ADMIN`. Users have exactly one role. Teachers already create and manage their own students (change 03), lessons and series, tasks and flashcards. The API is not exposed to browsers: educare-web calls it from its server.

## Goals / Non-Goals

**Goals:** a private teacher goes from the landing page to a working space in a few minutes; no change to how lessons, tasks and flashcards work; no weakening of tenant isolation.
**Non-Goals:** see proposal (billing, multi-school accounts, upgrade to school, social login).

## Decisions

- **Individual space = tenant with `kind = INDIVIDUAL`.** Reuses isolation, subdomain routing and every feature as is. Alternatives considered: teachers without a tenant (rewrites the isolation model, high risk); a "personal school" with an `ADMIN` + `TEACHER` pair for the same person (two logins or a multi-role user, confusing). `kind` is a column on `tenants` (`SCHOOL` default, so existing tenants are unchanged).
- **The private teacher is a plain `TEACHER`; no admin exists in an individual space.** Everything a private teacher needs is already allowed to teachers, except tenant settings, which get an explicit rule: `PUT /api/tenant` is allowed to `ADMIN`, or to `TEACHER` when the tenant is `INDIVIDUAL`. Admin-only endpoints (`/api/teachers`) answer 403 to the teacher, as today; no special case needed. This keeps a single role per user.
- **Public endpoints under `/api/public/**`**, excluded from `TenantFilter` and from authentication:
  - `GET /api/public/slug-availability?slug=` → `{available, reason?}` (`INVALID`, `RESERVED`, `TAKEN`).
  - `POST /api/public/signups` → 202 `{slug}`; creates the tenant and the unconfirmed teacher in one transaction (reusing `PlatformService` internals with `kind` and role parameters).
  - `POST /api/public/signups/confirm` `{token}` → 200 `{slug}`; sets `email_confirmed_at`.
  - `POST /api/public/signups/resend` `{email, slug}` → always 202 (never reveals whether the account exists).
- **Correcting a mistyped email**: signing up again for an address whose space is still unconfirmed, with the same password, updates that sign-up and sends a new link; anyone else gets 409. Without this, a typo would hold the address for 7 days.
- **Unconfirmed accounts cannot log in.** `users.email_confirmed_at` (null only for self sign-ups; existing and admin-created users are backfilled/created confirmed, because an admin vouches for them). Login with the right password but no confirmation → 403 with `code: EMAIL_NOT_CONFIRMED`, so the web can offer "resend". Wrong password still → 401 with the usual message.
- **Confirmation tokens**: 32 random bytes, URL-safe base64, sent by email; only the SHA-256 hash is stored in `email_confirmations(id, tenant_id, user_id, token_hash, expires_at, used_at)`. Valid 48 hours, single use. Resend invalidates previous tokens and is limited to 3 per hour per account.
- **Expiry**: a job (hourly, `!test` profile, same pattern as `LessonSeriesJob`) deletes `INDIVIDUAL` tenants whose only user is still unconfirmed after 7 days, freeing the slug.
- **Slugs**: same rules as provisioning (3-40, `[a-z0-9-]`), plus a reserved list (`www`, `app`, `api`, `admin`, `login`, `signup`, `mail`, `static`, `help`, `support`, `status`, `blog`, `docs`, `educare`, …) checked by both sign-up and operator provisioning. The DB unique constraint still decides races (409).
- **Rate limits** (in memory, per API instance, token bucket): sign-up 5/hour per client IP and 3/day per email; slug availability 60/minute per IP; confirm 20/hour per IP. Exceeded → 429 with `Retry-After`. The client IP comes from `X-Forwarded-For` only when the request comes from a trusted proxy address (`educare.security.trusted-proxies`, default loopback), because educare-web calls the API from its server and forwards the browser's IP.
- **Email**: `spring-boot-starter-mail`, a small `Mailer` interface with an SMTP implementation; messages in Portuguese (user-facing content; API error messages stay in English). Sent after the transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), so a rollback never sends a link. The confirmation link is `{web-base-url with slug}/confirm?token=…`, built from `educare.web.root-domain` and `educare.web.scheme`. Dev: Mailpit in `compose.yaml` (SMTP 1026, UI 8026, off the defaults so it can run beside other Mailpits); tests: a recording `Mailer` bean.
- **Password rules** for sign-up: same as change-password (at least 8 characters); the account starts with `mustChangePassword = false` (the teacher chose it).

## Risks / Trade-offs

- [In-memory rate limits reset on restart and are per instance] → Acceptable for one instance; move to Postgres or Redis when scaling out.
- [Spam sign-ups reserve good slugs for 7 days] → Rate limits plus expiry; the operator can delete a tenant manually.
- [Email deliverability (spam folders)] → Out of scope for code; production needs a transactional email provider with SPF/DKIM. Resend is available.
- [Trusting `X-Forwarded-For`] → Only from configured proxy addresses; otherwise the socket address is used.

## Migration Plan

`V12` adds `tenants.kind` (default `SCHOOL`), `users.email_confirmed_at` (backfilled with `created_at` for existing users) and `email_confirmations`. Fully backward compatible; rollback = drop the new column/table.

## Open Questions

- Should a private teacher be limited in number of students (e.g. a free tier)? Not in this change.
