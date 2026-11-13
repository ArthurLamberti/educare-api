# Proposal: Platform admin and password reset

## Why
The operator panel (change 11) used the shared platform key as a login. The system needs a real
admin account, created by default, and every user needs a way to recover a forgotten password.

## What Changes
- `platform_admins`: people who operate the platform, outside any tenant. At startup the API creates
  the admin from `educare.platform.admin-email` (default `arthurlamberti001@gmail.com`) if missing,
  with a random password nobody knows; the admin sets one through "forgot password".
- `POST /api/platform/auth/login` → a platform admin token (no tenant, claim `platform: true`), only
  accepted under `/api/platform/`. `/api/platform/**` accepts that token or the platform key.
- Forgot password for school users (`/api/auth/forgot-password`, `/api/auth/reset-password`, in the
  request's tenant) and platform admins (`/api/platform/auth/forgot-password`, `.../reset-password`):
  single-use links valid 1 hour, emailed after commit.

## Impact
- Migration V14 (`platform_admins`, `password_resets`).
- Web (educare-web change 12): forgot/reset pages; operator panel logs in with email and password.
