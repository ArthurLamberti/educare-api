# Proposal: Online lessons and lesson notes

## Why
Online lessons need the room link at hand for teacher and student, and teachers want to write what
happened in a lesson so the student can read it later.

## What Changes
- `meetingUrl` (http/https, up to 500) on lessons and weekly series; a lesson with a link is online.
  A series copies its link to every lesson it creates.
- `annotations` (free text, up to 10000) and `annotationsUpdatedAt` on lessons, written through
  `PUT /api/lessons/{id}/annotations` by the lesson's teacher at any time (also after the lesson or
  when it was canceled). Students and admins read them in the lesson.

## Impact
- Migration V15. `LessonResponse` and `LessonSeriesResponse` gain fields.
- Web (educare-web change 13): "Aula online" in the lesson form, "Entrar na sala", lesson page with notes.
