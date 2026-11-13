# Tasks

- [x] V13: `tenants.status` (check constraint, default `ACTIVE`) and `status_changed_at`
- [x] `TenantStatus` on `Tenant`, `TenantInfo` and `TenantSummary`
- [x] Sign-up creates `PENDING` spaces
- [x] `TenantFilter` answers 403 `ACCOUNT_INACTIVE` (with `status`) outside the allowed paths
- [x] `TenantActivationService` (activate, suspend, source; refuses activation before email confirmation)
- [x] Emails: operator notice on confirmation (`educare.mail.operator`), "space activated" to the teacher
- [x] Operator endpoints: list by status with owner, activate, suspend
- [x] Rate limit wrong platform keys per client IP
- [x] Tests (`TenantActivationTest`, sign-up tests activate their spaces)
- [x] README
