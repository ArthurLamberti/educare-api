# Tasks

- [x] V21: `task_categories` (tenant_id, teacher_id, name, unique lower(trim(name)) per teacher), `tasks.category_id` ON DELETE SET NULL
- [x] `TaskCategory` entity, repository, service, controller (teacher only; own categories; limit 100; 409 on duplicate name)
- [x] `TaskRequest.categoryId` (optional; another teacher's or unknown → 400 `errors.categoryId`); responses with `category`
- [x] `categoryId` filter on `GET /api/tasks` and `GET /api/assignments`
- [x] Tests: CRUD, duplicate name in another case, another teacher's category, delete keeps tasks, filters
