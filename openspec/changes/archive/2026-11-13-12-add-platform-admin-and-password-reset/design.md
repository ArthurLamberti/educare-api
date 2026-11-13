# Design: Platform admin and password reset

## No initial password
The bootstrapped admin gets a random password. "Esqueci a senha" doubles as first access: owning
the email is the proof. No password lives in config, the repository or logs.

## Tokens
Admin tokens use the same HMAC key as school tokens, without `tid` and with `platform: true`.
`TenantAwareJwtConverter` accepts them only when no tenant was resolved (`/api/platform/`), and
rejects school tokens there, so neither kind can be used in the other place. The admin's `active`
flag is checked on every request (platform traffic is small; no cache).

## Reset links
`password_resets` points at a user or a platform admin (exactly one). Same pattern as email
confirmations: 32 random bytes, only the SHA-256 stored, single use, 1 hour. A new link or a reset
invalidates older links. Asking always answers 202; past 3 emails per account per hour nothing more
is sent, silently (a 429 would reveal the account). A school user's link only works on that school's
subdomain, because the user lookup is tenant-filtered. Users who never confirmed a sign-up email get
nothing (they should confirm first). Resetting clears `mustChangePassword`.

## Known limit
Existing sessions stay valid after a reset until they expire (tokens are stateless). Revoking them
would need a per-user token version; left for later.
