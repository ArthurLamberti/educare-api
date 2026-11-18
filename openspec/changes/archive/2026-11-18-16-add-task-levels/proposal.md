# Proposal: Task levels

## Why
Teachers want to tag each task with the level it suits, so they can find the right exercise for each
student. Today the scale is CEFR (A1 to C2), but it may change later (another scale, or one per school),
so the levels must be data, not code.

## What Changes
- A `levels` table (code, name, position, active), seeded with the CEFR scale: A1 Iniciante,
  A2 Básico, B1 Intermediário, B2 Intermediário superior, C1 Avançado, C2 Proficiente (names are display
  content in Portuguese, unlike API messages).
- `GET /api/levels`: the active levels in order, for any signed-in user. Clients never hard-code the scale.
- Tasks get an optional `levelId` (must be an active level). Task and assignment responses carry
  `level { id, code, name }`. `GET /api/tasks` and `GET /api/assignments` accept a `levelId` filter.
- Changing the scale later: add levels and deactivate old ones (a deactivated level stays on the tasks
  that have it but cannot be chosen again). A per-school scale would add a nullable `tenant_id` to
  `levels` without changing the API.

## Impact
- Migration V20: `levels` (global, not tenant-owned) with seed rows, `tasks.level_id` (nullable FK).
- Web (educare-web change 16): level select in the task form, badge and filter in the task and
  assignment lists, loaded from `GET /api/levels`.
