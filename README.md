# Project Chimera: Autonomous Influencer Factory

Project Chimera is a spec-driven, governance-first platform for building and operating autonomous influencer agents.

This repository provides an industry-standard baseline focused on:

- Java 21 immutable domain models (`record`)
- Planner -> Worker -> Judge swarm orchestration
- MCP-first integration contracts
- Human-in-the-loop governance and security controls
- automated backend and frontend verification
- CI/CD, linting, automation, containerization, and documentation

## Tech Stack

- Java 21
- Maven 3.8+
- JUnit 5 + AssertJ
- Spotless (format/lint gate)
- Docker
- GitHub Actions

## Top-Level Layout

```text
.
├── specs/
├── tests/
├── skills/
├── reports/
├── frontend/
├── src/
├── .github/workflows/
├── .cursor/rules/
└── Makefile
```

## Repository Structure

- `specs/`: spec-first source of truth
- `tests/`: top-level TDD contract tests plus a submission index for the wider test suite
- `skills/`: runtime skill structure and skill-specific instructions
- `reports/`: research report, project report, Loom script, and submission checklist
- `Makefile`: standard local and CI task entrypoints
- `.github/workflows/`: CI/CD workflow definitions
- `.cursor/rules/`: IDE agent governance rules
- `src/main/java/org/chimera/`: backend domain and orchestration skeleton
- `src/main/resources/soul/SOUL.md`: versioned agent persona source
- `src/test/java/org/chimera/tests/`: backend contract and regression tests
- `research/`: architecture and tooling strategy
- `docs/`: architecture, security, frontend, and governance docs
- `.cursor/mcp.json`, `.vscode/mcp.json`: agent and MCP configuration
- `db/migrations/`: SQL schema baseline

## Quickstart

```bash
make setup
make lint
make spec-check
make test
```

`make test` runs the current backend test suite.

Load validation:

```bash
make load-validate
make benchmark-sustained
make benchmark-redis-cluster
```

See `docs/load_validation.md` for distributed-mode and throughput validation workflow.

## Container Runtime

```bash
make docker-build
make docker-test
make docker-up
make docker-logs
```

This starts `api`, `postgres`, and `redis` with health-checked dependencies.

Stop stack:

```bash
make docker-down
```

## API Run

```bash
PORT=8080 make run
```

API authentication keys are loaded from `CHIMERA_API_KEYS`.
Format: `key:tenant:role1,role2;key2:tenant2:role1`.
Optional bearer auth can be enabled with:
- `CHIMERA_JWT_HS256_SECRET` for HS256 tokens, or
- `CHIMERA_JWT_JWKS_URL` for RS256 tokens validated by `kid` against JWKS (preferred), or
- `CHIMERA_JWT_JWKS_PATH` for local RS256 JWKS files.
Optional claim constraints:
- `CHIMERA_JWT_ISSUER`
- `CHIMERA_JWT_AUDIENCE`
- `CHIMERA_JWT_JWKS_REFRESH_SECONDS` (default `60`)
- `CHIMERA_JWT_JWKS_HTTP_TIMEOUT_MS` (default `2000`)
Write-rate limiter tuning:
- `CHIMERA_WRITE_RATE_LIMIT_MAX_REQUESTS` (default `30`)
- `CHIMERA_WRITE_RATE_LIMIT_WINDOW_SECONDS` (default `60`)
Orchestration tuning:
- `CHIMERA_DAILY_BUDGET_USD` (default `500.00`) used by judge budget policy for transaction tasks.
- `CHIMERA_CDP_BASE_URL` (default `https://api.coinbase.com/agentkit/v1`) for Coinbase AgentKit wallet API.
- `CHIMERA_QUEUE_MAX_RETRIES` (default `2`) for retry attempts before dead-lettering failed tasks.
- `CHIMERA_REPLAY_COOLDOWN_SECONDS` (default `300`) minimum cooldown between accepted manual replays of the same task.
- `CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY` (default `3`) max accepted manual replays per task per UTC day.
- `CHIMERA_OPENCLAW_SIGNING_SECRET` optional HMAC secret used to sign `openclaw.publish_status` payloads.
- `CHIMERA_CORS_ALLOWED_ORIGINS` (default local frontend origins) for browser preflight/origin policy.
- `CHIMERA_MCP_RESOURCE_ENDPOINT` optional live MCP resource HTTP endpoint (for perception reads).
- `CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS` (default `4`) timeout for MCP resource HTTP reads.
- `CHIMERA_MCP_RESOURCE_AUTHORIZATION` optional authorization header value for MCP resource HTTP reads.

Write-rate limiter backend order:
1. Redis token bucket when `REDIS_URL` is configured and reachable.
2. JDBC shared-window limiter (`api_rate_limits`) when PostgreSQL is configured.
3. In-memory limiter as local fallback.

```bash
export CHIMERA_API_KEYS='dev-tenant-alpha-key:tenant-alpha:operator,reviewer,viewer;dev-tenant-beta-key:tenant-beta:operator,reviewer,viewer'
export CHIMERA_JWT_HS256_SECRET='replace-with-strong-secret'
export CHIMERA_JWT_ISSUER='chimera'
export CHIMERA_JWT_AUDIENCE='chimera-agent-infra'
export CHIMERA_JWT_JWKS_URL='https://idp.example.com/.well-known/jwks.json'
export CHIMERA_JWT_JWKS_PATH='/absolute/path/to/jwks.json'
export CHIMERA_JWT_JWKS_REFRESH_SECONDS='60'
export CHIMERA_JWT_JWKS_HTTP_TIMEOUT_MS='2000'
export CHIMERA_WRITE_RATE_LIMIT_MAX_REQUESTS='30'
export CHIMERA_WRITE_RATE_LIMIT_WINDOW_SECONDS='60'
export CHIMERA_DAILY_BUDGET_USD='500.00'
export CHIMERA_CDP_BASE_URL='https://api.coinbase.com/agentkit/v1'
export CHIMERA_QUEUE_MAX_RETRIES='2'
export CHIMERA_REPLAY_COOLDOWN_SECONDS='300'
export CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY='3'
export CHIMERA_OPENCLAW_SIGNING_SECRET='replace-with-openclaw-signing-secret'
export CHIMERA_CORS_ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173'
export CHIMERA_MCP_RESOURCE_ENDPOINT='http://localhost:9090/resource'
export CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS='4'
export CHIMERA_MCP_RESOURCE_AUTHORIZATION='Bearer replace-with-mcp-token'
PORT=8080 make run
```

To enable PostgreSQL-backed persistence at runtime:

```bash
export POSTGRES_URL='jdbc:postgresql://localhost:5432/chimera'
export POSTGRES_USER='chimera'
export POSTGRES_PASSWORD='chimera'
PORT=8080 make run
```

### Endpoint Examples

```bash
curl -X POST http://localhost:8080/api/campaigns \\
  -H 'Content-Type: application/json' \\
  -H 'X-Api-Key: dev-tenant-alpha-key' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: operator' \\
  -d '{"goal":"Launch urban fashion teaser","workerId":"worker-alpha","requiredResources":["news://ethiopia/fashion/trends"]}'

curl http://localhost:8080/api/tasks \\
  -H 'X-Api-Key: dev-tenant-alpha-key' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: viewer'

curl http://localhost:8080/api/telemetry \\
  -H 'X-Api-Key: dev-tenant-alpha-key' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: viewer'

curl -X POST http://localhost:8080/api/review/{taskId}/approve \\
  -H 'X-Api-Key: dev-tenant-alpha-key' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: reviewer'

curl -X POST http://localhost:8080/api/dead-letter/{taskId}/replay \\
  -H 'X-Api-Key: dev-tenant-alpha-key' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: operator'

curl -X POST http://localhost:8080/api/campaigns \\
  -H 'Content-Type: application/json' \\
  -H 'Authorization: Bearer <jwt-token>' \\
  -H 'X-Tenant-Id: tenant-alpha' \\
  -H 'X-Role: operator' \\
  -d '{"goal":"JWT-authenticated campaign","workerId":"worker-alpha"}'

curl http://localhost:8080/openapi.yaml
```

## Frontend Run

```bash
cd frontend
npm install
npm run dev
npm run test:e2e:install
npm run test:e2e
npm run test:e2e:live
```

Default UI URL: `http://localhost:5173`

The frontend uses tenant/auth headers against:
- `GET /api/tasks`
- `GET /api/telemetry`
- `POST /api/campaigns`
- `POST /api/review/{taskId}/approve`
- `POST /api/review/{taskId}/reject`
- `POST /api/dead-letter/{taskId}/replay`

Campaign creation now triggers planner -> worker -> judge orchestration in-process, so tasks can progress immediately from `PENDING` to governed statuses (`COMPLETE`, `ESCALATED`, `REJECTED`) based on confidence and policy checks.
Planner context is persona-aware: `SOUL.md` directives and memory recall are injected through the cognitive context assembler before task decomposition.
Runtime queue governance includes retry + dead-letter handling for execution failures.
Generate-content worker execution now uses a creative engine MCP pipeline:
- `creative.generate_text`
- `creative.generate_image`
- `creative.generate_video`
- `creative.check_consistency`
Failed/low-score consistency locks are escalated for HITL visual review by judge policy before publish is allowed.
Social execution routes platform-specific tool calls from task resources (`twitter://`, `instagram://`, `threads://`) and applies bounded retry/backoff for transient MCP failures.
Operator users can replay dead-lettered/rejected tasks back to `PENDING` using the replay endpoint or the Task Ledger replay action.
Replay governance is enforced with a per-task cooldown and daily replay cap, and every replay decision is written to persistent audit storage when PostgreSQL is configured.
Operational response steps are documented in `docs/replay_governance_runbook.md`.
Task orchestration now also emits best-effort OpenClaw agent status updates through `openclaw.publish_status` on worker state transitions, with signed payloads and local audit retention.

Transaction execution path:
- `WorkerService` handles `EXECUTE_TRANSACTION` tasks via `WalletExecutionService`.
- If `CDP_API_KEY_NAME` and `CDP_API_KEY_PRIVATE_KEY` are present, Coinbase AgentKit provider is used.
- Coinbase wallet requests are signed (HMAC) before transport using `X-CDP-SIGNATURE` and `X-CDP-TIMESTAMP`.
- If Coinbase secrets are absent, runtime falls back to deterministic `SimulatedWalletProvider`.
- Approved transaction executions are persisted to `api_wallet_ledger`.
- Judge budget checks now use tenant daily spend derived from ledger state.
- `GET /api/telemetry` includes wallet financial metrics:
  - `todaySpendUsd`
  - `remainingBudgetUsd`
  - `walletTransfersToday`
  - `spendDeltaVsYesterdayUsd`
- `GET /api/telemetry` includes perception telemetry:
  - `trendSignalsToday`
  - `topTrendTopicsToday`
- `GET /api/telemetry` also exposes queue resilience counters:
  - `deadLetterQueueDepth`
  - `retryAttemptsToday`
  - `deadLetteredTasksToday`
- `GET /api/telemetry` exposes worker SLO counters:
  - `workerP50LatencyMs`
  - `workerP95LatencyMs`
  - `successfulExecutionsToday`
  - `failedExecutionsToday`
