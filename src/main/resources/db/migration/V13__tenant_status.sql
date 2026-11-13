-- Whether a space may be used. Schools (provisioned by the operator) are active from the start;
-- private teachers' spaces start PENDING until the operator (later: a confirmed payment) activates them.
ALTER TABLE tenants ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'));
ALTER TABLE tenants ADD COLUMN status_changed_at TIMESTAMPTZ;
UPDATE tenants SET status_changed_at = created_at;
ALTER TABLE tenants ALTER COLUMN status_changed_at SET NOT NULL;
CREATE INDEX ix_tenants_status ON tenants (status, created_at);
