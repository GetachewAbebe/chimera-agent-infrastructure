CREATE TABLE IF NOT EXISTS api_rate_limits (
    limiter_key TEXT PRIMARY KEY,
    window_start_ms BIGINT NOT NULL,
    request_count INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_api_rate_limits_updated_at ON api_rate_limits(updated_at);
