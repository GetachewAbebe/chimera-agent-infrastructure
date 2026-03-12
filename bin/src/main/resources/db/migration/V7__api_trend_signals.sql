CREATE TABLE IF NOT EXISTS api_trend_signals (
    signal_id UUID PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    topic TEXT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    source TEXT NOT NULL,
    observed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_api_trend_signals_tenant_observed_at
    ON api_trend_signals (tenant_id, observed_at DESC);

CREATE INDEX IF NOT EXISTS idx_api_trend_signals_tenant_topic
    ON api_trend_signals (tenant_id, topic);
