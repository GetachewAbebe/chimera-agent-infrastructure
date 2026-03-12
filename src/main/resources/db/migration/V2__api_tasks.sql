CREATE TABLE IF NOT EXISTS api_tasks (
    task_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    task_type TEXT NOT NULL,
    priority TEXT NOT NULL,
    context_json TEXT NOT NULL,
    assigned_worker_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_tasks_created_at ON api_tasks(created_at);
CREATE INDEX IF NOT EXISTS idx_api_tasks_status ON api_tasks(status);
CREATE INDEX IF NOT EXISTS idx_api_tasks_tenant_id ON api_tasks(tenant_id);
