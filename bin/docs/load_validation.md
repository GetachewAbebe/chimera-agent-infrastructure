# Load Validation Playbook

This document defines repeatable throughput checks for swarm worker execution and write-path queue pressure.

## Goals

- Validate concurrent worker execution behavior under virtual-thread load.
- Validate API write-path behavior under concurrent campaign creation.
- Verify expected `201`/`429` outcomes without unexpected 5xx/4xx noise.
- Track write-path latency SLO metrics (`avg`, `p50`, `p95`, `max`) for accepted requests.

## 1) Automated Worker Concurrency Test (JUnit)

The baseline load assertion runs in:

- `src/test/java/org/chimera/tests/worker/WorkerServiceLoadTest.java`

It validates that a concurrent batch of worker tasks completes inside an SLO threshold.

Optional tuning properties:

- `-Dchimera.load.taskCount=200`
- `-Dchimera.load.simulatedWorkMs=40`
- `-Dchimera.load.maxElapsedMs=4000`

Run:

```bash
make test
```

## 2) API Write-Path Load Validation Script

Script:

- `scripts/load_validation.sh`

Run prerequisites:

1. Start API (`make run`) or Docker stack (`make docker-up`).
2. Ensure API auth key/header values match runtime configuration.

Run:

```bash
make load-validate
```

Environment overrides:

- `API_BASE_URL` (default `http://localhost:8080`)
- `API_KEY` (default `dev-tenant-alpha-key`)
- `TENANT_ID` (default `tenant-alpha`)
- `ROLE` (default `operator`)
- `TOTAL_REQUESTS` (default `120`)
- `CONCURRENCY` (default `24`)
- `REQUEST_TIMEOUT_SECONDS` (default `10`)
- `GOAL_PREFIX` (default `Load validation campaign`)
- `OUTPUT_JSON_PATH` (optional JSON artifact output path)

Expected outcome:

- `HTTP 201` should be present.
- `HTTP 429` may appear due write-rate governance.
- unexpected statuses should be `0`.
- latency metrics should remain inside target SLOs for your run profile.

Summary now includes:

- `Latency avg ms (201)`
- `Latency p50 ms (201)`
- `Latency p95 ms (201)`
- `Latency max ms (201)`

## 3) Distributed Runtime Validation

For Redis + PostgreSQL mode:

```bash
export POSTGRES_URL='jdbc:postgresql://localhost:5432/chimera'
export POSTGRES_USER='chimera'
export POSTGRES_PASSWORD='chimera'
export REDIS_URL='redis://localhost:6379'
make run
```

Then run:

```bash
TOTAL_REQUESTS=300 CONCURRENCY=50 make load-validate
```

Record in Day 2 submission:

- request count
- concurrency
- elapsed seconds
- throughput req/s
- status distribution (`201`, `429`, unexpected)
- latency summary (`avg`, `p50`, `p95`, `max`)

## 4) Sustained Benchmark Automation

Script:

- `scripts/sustained_benchmark.sh`

Run:

```bash
make benchmark-sustained
```

Optional tuning:

- `ROUNDS` (default `5`)
- `TOTAL_REQUESTS` (default `200`)
- `CONCURRENCY` (default `40`)
- `OUTPUT_DIR` (default timestamped under `artifacts/benchmarks/`)

Artifacts:

- per-round logs (`round_N.log`)
- per-round JSON (`round_N.json`)
- aggregate CSV (`rounds.csv`)
- aggregate report (`summary.md`)

## 5) Redis Cluster Stress Profile

Script:

- `scripts/redis_cluster_stress.sh`

Run:

```bash
export REDIS_URL='redis://localhost:6379'
ROUNDS=3 TOTAL_REQUESTS=800 CONCURRENCY=120 make benchmark-redis-cluster
```

See `docs/redis_cluster_stress_profile.md` for details and output artifact paths.
