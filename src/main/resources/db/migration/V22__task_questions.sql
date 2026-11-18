-- Open questions of a task, in order. Answers are identified by position, not by question id: the
-- submission keeps its own copy of the prompts (like the instructions snapshot), so editing the task
-- never changes what a student answered.
CREATE TABLE task_questions (
    task_id  BIGINT        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    position INTEGER       NOT NULL,
    prompt   VARCHAR(2000) NOT NULL,
    PRIMARY KEY (task_id, position)
);

-- The prompts as they were at the first submission, with the student's current answers.
CREATE TABLE submission_answers (
    submission_id BIGINT        NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    position      INTEGER       NOT NULL,
    prompt        VARCHAR(2000) NOT NULL,
    answer        VARCHAR(5000) NOT NULL,
    PRIMARY KEY (submission_id, position)
);

-- The student's private draft answers (change 15), one per question position; may be blank.
CREATE TABLE assignment_draft_answers (
    assignment_id BIGINT        NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    position      INTEGER       NOT NULL,
    answer        VARCHAR(5000) NOT NULL,
    PRIMARY KEY (assignment_id, position)
);
