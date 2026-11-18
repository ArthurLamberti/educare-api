-- Virtual rooms created by Educare for online lessons. Only a provider-neutral key is stored; the
-- provider's link is built when someone joins, so the provider can change without touching lessons.
-- A lesson has either a room key or its own meeting_url (a link the teacher pasted), never both.
ALTER TABLE lessons ADD COLUMN virtual_room_key VARCHAR(64);
ALTER TABLE lessons ADD CONSTRAINT uq_lessons_virtual_room_key UNIQUE (virtual_room_key);
ALTER TABLE lessons ADD CONSTRAINT ck_lessons_one_room CHECK (virtual_room_key IS NULL OR meeting_url IS NULL);

-- A series with a virtual room gives each of its lessons a room of its own.
ALTER TABLE lesson_series ADD COLUMN virtual_room BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lesson_series ADD CONSTRAINT ck_lesson_series_one_room CHECK (NOT virtual_room OR meeting_url IS NULL);
