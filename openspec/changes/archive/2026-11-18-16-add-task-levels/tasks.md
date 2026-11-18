# Tasks

- [x] V20: `levels` (id, code unique, name, position, active) seeded A1–C2; `tasks.level_id` FK
- [x] `Level` entity (not tenant-owned), repository, `GET /api/levels`
- [x] `TaskRequest.levelId` (optional; unknown or inactive → 400 `errors.levelId`); responses with `level`
- [x] `levelId` filter on `GET /api/tasks` and `GET /api/assignments`
- [x] Tests: list order, create/edit with level, inactive level rejected but kept on existing tasks, filters
