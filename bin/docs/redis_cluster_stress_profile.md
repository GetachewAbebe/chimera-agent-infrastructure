# Redis Cluster Stress Profile

This profile validates queue behavior and sustained throughput when Chimera uses Redis-backed queues.

## Prerequisites

- Redis endpoint reachable through `REDIS_URL`.
- Maven dependencies installed (`make setup`).
- API key environment defaults available (`CHIMERA_API_KEYS` or built-in dev key setup).

## Run

```bash
export REDIS_URL='redis://localhost:6379'
ROUNDS=3 TOTAL_REQUESTS=800 CONCURRENCY=120 bash scripts/redis_cluster_stress.sh
```

## Outputs

- Summary report: `artifacts/benchmarks/redis-cluster-*/summary.md`
- Round metrics: `artifacts/benchmarks/redis-cluster-*/rounds.csv`
- Per-round JSON: `artifacts/benchmarks/redis-cluster-*/round_*.json`
- API runtime log: `artifacts/benchmarks/redis-cluster-*/api.log`

## Suggested SLO Review

- p95 request latency remains within acceptable threshold for sustained runs.
- Unexpected statuses remain `0`.
- Rate-limited responses are explainable by configured throughput limits.
