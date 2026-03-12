CREATE TABLE IF NOT EXISTS api_wallet_ledger (
    entry_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    task_id UUID NOT NULL,
    worker_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    transaction_id TEXT NOT NULL,
    amount_usd NUMERIC(18, 2) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_wallet_ledger_tenant_date
    ON api_wallet_ledger (tenant_id, executed_at);

CREATE INDEX IF NOT EXISTS idx_api_wallet_ledger_task_id
    ON api_wallet_ledger (task_id);
