# DB and Data Management

## Storage Responsibilities

- PostgreSQL: campaigns, tasks, reviews, wallets, audit metadata
- PostgreSQL: distributed write-rate limiter state (`api_rate_limits`) for multi-instance API throttling
- PostgreSQL: wallet transfer ledger (`api_wallet_ledger`) for daily spend aggregation and budget governance
- Redis: queueing, short-lived context cache, and token-bucket rate-limit keys (`chimera:ratelimit:*`)
- Vector DB: semantic memories and retrieval references

Rate-limit storage precedence at runtime:
- Redis token bucket when `REDIS_URL` is configured and reachable.
- PostgreSQL `api_rate_limits` shared state when Redis is unavailable and DB is configured.
- In-memory counters when neither Redis nor PostgreSQL is available.

## Migration Strategy

- SQL migrations in `db/migrations`
- Forward-only versioning
- One migration per logical schema change
- Runtime migrations via Flyway classpath scripts (`src/main/resources/db/migration`)

## Data Lifecycle

- Task and review records retained for auditability
- Reasoning traces redacted if secrets are detected
- Wallet activity retained for compliance reporting and budget reconciliation
