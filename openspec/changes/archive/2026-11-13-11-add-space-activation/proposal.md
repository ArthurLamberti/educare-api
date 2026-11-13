# Proposal: Space activation

## Why
A private teacher who signs up (change 10) can use the platform right after confirming the email.
The space should only be usable after someone confirms it: at first the platform operator, later a
confirmed payment through a payment gateway.

## What Changes
- Tenants get a status: `PENDING`, `ACTIVE` or `SUSPENDED`. Schools provisioned by the operator, and
  all existing tenants, are `ACTIVE`. Private spaces start `PENDING`.
- Users of a space that is not `ACTIVE` can log in, read `/api/auth/me` and `GET /api/tenant`, and
  change their password. Everything else answers 403 with `code: ACCOUNT_INACTIVE`.
- Operator endpoints (`X-Platform-Key`): list spaces by status with their owner, activate, suspend.
- One service changes the status (`TenantActivationService`), with a source (`OPERATOR`, later
  `PAYMENT`). The gateway integration will only call it.
- Emails: the operator is told when a confirmed space waits (`educare.mail.operator`); the teacher
  is told when the space is activated.
- Wrong platform keys are rate limited per client IP (10 per 15 minutes), since the web now has a
  form where the key is typed.

## Impact
- Migration V13 (`tenants.status`, `tenants.status_changed_at`).
- `TenantSummary` (login, `/api/auth/me`, `/api/tenant`) gains `status`.
- Web (educare-web change 11): `/pending` page and operator panel at `/admin` on the root domain.
