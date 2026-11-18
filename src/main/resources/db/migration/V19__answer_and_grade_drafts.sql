-- Private drafts kept on the assignment (not on the submission, whose version tracks the answer the
-- teacher reviewed). The student's draft is never shown to the teacher; the teacher's correction draft
-- is never shown to the student. Sending the answer or the grade clears the matching draft.
ALTER TABLE assignments
    ADD COLUMN answer_draft          VARCHAR(20000),
    ADD COLUMN answer_draft_saved_at TIMESTAMPTZ,
    ADD COLUMN grade_draft_score     NUMERIC(3, 1),
    ADD COLUMN grade_draft_feedback  VARCHAR(10000),
    ADD COLUMN grade_draft_saved_at  TIMESTAMPTZ,
    ADD CONSTRAINT ck_assignments_grade_draft_score CHECK (grade_draft_score BETWEEN 0 AND 10);
