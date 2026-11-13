# Design: Space activation

## Status on the tenant, checked per request
The status lives on `tenants` and is cached in `TenantDirectory` (evicted on every change).
`TenantFilter` already resolves the tenant of every request, so it also refuses requests to inactive
spaces. The status is not put in the JWT, so activating or suspending takes effect on the next
request, without logging in again.

Allowed while inactive: `/api/auth/**` (login, me, change-password) and `GET /api/tenant`. Login is
allowed on purpose: the teacher lands on a page that explains the wait, and that page is where a
"pay to activate" step will go.

## Order: email first, then activation
Activation is refused (422) while nobody in the space has confirmed the email. Otherwise an activated
space could still be deleted by the cleanup of unconfirmed sign-ups (7 days). The operator notice is
sent when the email gets confirmed, so the operator is only asked about real people.

## Payment gateway later
`TenantActivationService.activate(tenantId, ActivationSource.PAYMENT)` from the gateway webhook (after
checking its signature), and `suspend(..., PAYMENT)` when a subscription lapses. Expected additions
then: a `subscriptions` table (gateway customer and subscription ids, period end), a public webhook
endpoint, and a checkout link on the web's `/pending` page. The status and the blocking stay as they are.

## Operator access
There are no operator accounts yet; the operator uses the platform key. The web panel keeps it in an
httpOnly cookie on the root domain. Wrong keys are limited per IP in `PlatformKeyFilter`. Operator
accounts (with their own login and audit) are the next step if more people operate the platform.
