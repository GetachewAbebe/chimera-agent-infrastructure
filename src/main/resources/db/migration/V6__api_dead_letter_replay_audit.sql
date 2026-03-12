CREATE TABLE IF NOT EXISTS api_dead_letter_replay_audit (
    event_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    task_id UUID NOT NULL,
    accepted BOOLEAN NOT NULL,
    reason TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_dead_letter_replay_audit_task_time
    ON api_dead_letter_replay_audit (tenant_id, task_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_api_dead_letter_replay_audit_task_day
    ON api_dead_letter_replay_audit (tenant_id, task_id, accepted, occurred_at);
