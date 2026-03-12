ALTER TABLE api_tasks
ADD COLUMN IF NOT EXISTS tenant_id TEXT;

UPDATE api_tasks
SET tenant_id = 'default_tenant'
WHERE tenant_id IS NULL OR tenant_id = '';

ALTER TABLE api_tasks
ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_api_tasks_tenant_id ON api_tasks(tenant_id);
