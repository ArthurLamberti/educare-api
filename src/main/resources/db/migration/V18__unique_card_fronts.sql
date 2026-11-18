-- A student never has two cards with the same front: compared trimmed, with inner whitespace collapsed
-- and lowercased (accents count). Card.keyOf computes the same key in the application.
ALTER TABLE cards ADD COLUMN front_key VARCHAR(1000);

UPDATE cards SET front_key = lower(regexp_replace(trim(front), '\s+', ' ', 'g'));

-- Merge duplicates created before this rule: keep the card with the most recent review (the oldest
-- card when none was reviewed); the others and their review history are deleted.
DELETE FROM cards c
USING (
    SELECT id,
           row_number() OVER (
               PARTITION BY tenant_id, student_id, front_key
               ORDER BY last_reviewed_at DESC NULLS LAST, id
           ) AS rank
    FROM cards
) ranked
WHERE c.id = ranked.id AND ranked.rank > 1;

ALTER TABLE cards ALTER COLUMN front_key SET NOT NULL;

CREATE UNIQUE INDEX ux_cards_student_front ON cards (tenant_id, student_id, front_key);
