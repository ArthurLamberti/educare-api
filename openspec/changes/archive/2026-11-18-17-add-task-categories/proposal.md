# Proposal: Task categories

## Why
Each teacher organizes exercises their own way (Grammar, Vocabulary, Listening, Exam prep...). A
category per task lets them group and filter their library.

## What Changes
- Each teacher manages their own categories: `GET/POST /api/task-categories`,
  `PUT/DELETE /api/task-categories/{id}`, with `name` (required, max 60, unique per teacher ignoring
  case and surrounding spaces). At most 100 per teacher. Other teachers' categories answer 404.
- Tasks get an optional `categoryId` (one of the teacher's categories). Task and assignment responses
  carry `category { id, name }`; `GET /api/tasks` and `GET /api/assignments` accept a `categoryId` filter.
- Deleting a category leaves its tasks without a category. Each category reports how many tasks use it.

## Impact
- Migration V21: `task_categories` (tenant-owned, per teacher), `tasks.category_id` (nullable FK,
  `ON DELETE SET NULL`).
- Web (educare-web change 17): category management, category select (with "Nova categoria") in the
  task form, badge and filter in the lists.
