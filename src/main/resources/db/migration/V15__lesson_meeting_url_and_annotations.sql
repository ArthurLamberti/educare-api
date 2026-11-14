-- Online lessons: the room link (Meet, Zoom, ...). A lesson with a link is an online lesson.
ALTER TABLE lessons ADD COLUMN meeting_url VARCHAR(500);
ALTER TABLE lessons ADD CONSTRAINT ck_lessons_meeting_url CHECK (meeting_url ~ '^https?://');
ALTER TABLE lesson_series ADD COLUMN meeting_url VARCHAR(500);
ALTER TABLE lesson_series ADD CONSTRAINT ck_lesson_series_meeting_url CHECK (meeting_url ~ '^https?://');

-- The teacher's free-text notes about a lesson (during or after it), shown to the student.
ALTER TABLE lessons ADD COLUMN annotations VARCHAR(10000);
ALTER TABLE lessons ADD COLUMN annotations_updated_at TIMESTAMPTZ;
