# Backend API and Orchestration Notes

## HTTP Endpoints

- `POST /api/campaigns`
  - Creates campaign tasks using planner decomposition and executes immediate planner-worker-judge orchestration.
- `GET /api/tasks`
  - Lists currently tracked tasks.
- `GET /api/telemetry`
  - Returns queue depth, status counts, queue backend mode, wallet runtime mode, wallet budget telemetry (`todaySpendUsd`, `remainingBudgetUsd`, `walletTransfersToday`, `spendDeltaVsYesterdayUsd`), perception telemetry (`trendSignalsToday`, `topTrendTopicsToday`), and queue resilience/SLO counters (`deadLetterQueueDepth`, `retryAttemptsToday`, `deadLetteredTasksToday`, `workerP50LatencyMs`, `workerP95LatencyMs`, `successfulExecutionsToday`, `failedExecutionsToday`) for the tenant.
- `POST /api/review/{taskId}/approve`
  - Marks task as human-approved (`COMPLETE`).
- `POST /api/review/{taskId}/reject`
  - Marks task as human-rejected (`REJECTED`).
- `POST /api/dead-letter/{taskId}/replay`
  - Requeues a rejected/dead-letter candidate task back to `PENDING` when replay governance policy allows.
- `GET /openapi.yaml`
  - Exposes API contract for clients and governance review.

## Runtime Components

- `CampaignApiService`: validates campaign requests and invokes planner.
- `TaskRepository`: persistence contract for task state.
- `InMemoryTaskRepository`: default storage when no database is configured.
- `JdbcTaskRepository`: PostgreSQL-backed storage for persistent task state.
- `ReviewApiService`: applies human review decisions.
- `DeadLetterApiService`: validates and replays dead-lettered/rejected tasks back into `task_queue`, with cooldown and daily replay caps.
- `DeadLetterReplayAuditRepository`: contract for replay governance audit events.
- `JdbcDeadLetterReplayAuditRepository`: PostgreSQL-backed replay audit storage (`api_dead_letter_replay_audit`).
- `InMemoryDeadLetterReplayAuditRepository`: local replay audit fallback for non-database runtime.
- `ChimeraHttpServer`: JSON HTTP server using Java virtual-thread executor.
- `PersistenceBootstrap`: switches between in-memory and JDBC repositories and runs Flyway migrations.
- `RedisRequestRateLimiter`: distributed token-bucket limiter for low-latency shared write throttling.
- `JdbcRequestRateLimiter`: shared write-rate limiter backed by PostgreSQL table state.
- `McpPerceptionService`: polls MCP resources and filters relevant signals by semantic score threshold.
- `HttpMcpResourceClient`: optional MCP resource adapter that resolves `resourceUri` values through an HTTP gateway endpoint.
- `CognitiveContextAssembler`: assembles planner context with `SOUL.md` persona directives and memory recall.
- `SoulMarkdownPersonaLoader` / `ClasspathSoulPersonaLoader`: load immutable persona data from versioned `SOUL.md`.
- `InMemoryMemoryRecall`: deterministic memory retrieval baseline for local runtime.
- `SocialPublishingService`: maps platform-agnostic publish/reply requests to MCP tool calls.
- `CreativeEngineService`: composes multimodal draft artifacts through MCP creative tools and enforces consistency lock inputs.
- `TaskOrchestratorService`: drains queued tasks, runs worker execution, applies judge policy, retries transient execution failures, and dead-letters exhausted tasks.
- `McpOpenClawStatusPublisher`: publishes best-effort `openclaw.publish_status` updates on orchestrator state transitions using signed tenant-scoped payloads.
- `WorkerService`: executes MCP-backed social actions plus governed transaction execution (`EXECUTE_TRANSACTION`).
- `WorkerService`: resolves social platform from task resources (Twitter/Instagram/Threads) and applies retry/backoff on MCP action failures.
- `JudgeService`: enforces confidence thresholds, sensitive-topic escalation, OCC checks, and budget gating for transaction tasks.
- `SensitiveTopicClassifier`: centralized keyword/pattern policy for sensitive-domain escalation.
- `JudgeService`: includes creative consistency review hooks (`creative_consistency_passed`, `creative_consistency_score`) for generate-content governance.
- `TelemetryApiService`: aggregates queue depth and task status metrics for dashboard runtime visibility.
- `WalletLedgerRepository`: contract for tenant transaction ledger persistence and daily budget aggregation.
- `JdbcWalletLedgerRepository`: PostgreSQL-backed wallet ledger storage (`api_wallet_ledger`).
- `InMemoryWalletLedgerRepository`: local fallback ledger for non-database runtime.
- `TrendSignalRepository`: contract for trend telemetry persistence and daily query aggregation.
- `JdbcTrendSignalRepository`: PostgreSQL-backed trend signal storage (`api_trend_signals`).
- `InMemoryTrendSignalRepository`: local fallback trend-signal storage for non-database runtime.
- `RedisTaskQueuePort` and `RedisUuidQueuePort`: Redis-backed distributed queue implementations for `task_queue` and `review_queue`.
- `InMemoryQueueGovernanceMetrics`: tenant-scoped daily counters for retry and dead-letter events.
- `WalletExecutionService`: parses transaction resources and executes governed wallet transfers.
- `CoinbaseAgentKitWalletProvider`: Coinbase/CDP provider adapter backed by environment secrets.
- `CoinbaseAgentKitWalletProvider`: signs outbound wallet requests (`X-CDP-SIGNATURE`, `X-CDP-TIMESTAMP`) with HMAC to avoid raw private-key header transport.
- `SimulatedWalletProvider`: deterministic local fallback when Coinbase credentials are not configured.

## Header-Based Access Control

- Required headers:
  - `X-Tenant-Id`
  - `X-Role`
- Authentication headers (one required):
  - `X-Api-Key`
  - `Authorization: Bearer <jwt>`
- Role matrix:
  - `operator`: can create campaigns
  - `reviewer`: can approve/reject review actions
  - `viewer`: can list tasks
- API keys are scoped to a single tenant and an allow-list of roles.
- JWT bearer tokens are validated for signature (HS256 or RS256), expiry, issuer/audience (optional), tenant, and role set.
- RS256 uses `kid`-indexed keys from JWKS (URL or file) with timed reload support for key rotation.
- Remote JWKS fetch applies request timeout and fails closed on reload/fetch failures.
- Write endpoints are rate-limited and return `429` with `rate_limited` error code.
- Rate-limit backend precedence: Redis token bucket -> JDBC shared window -> local in-memory.
- Task listing and review updates are tenant-scoped.
- Worker reply generation includes an honesty directive for AI-identity inquiries.
- Social publishing layer enforces non-`NONE` disclosure before external MCP action execution.

## Browser CORS

- API handlers support `OPTIONS` preflight on all public endpoints.
- Allowed CORS origins are configurable via `CHIMERA_CORS_ALLOWED_ORIGINS`.
- Default allowed origins cover local frontend hosts (`localhost`/`127.0.0.1` on ports `5173` and `4173`).

## MCP Resource Adapter Runtime

- `CHIMERA_MCP_RESOURCE_ENDPOINT` enables live HTTP-backed resource reads for perception.
- `CHIMERA_MCP_RESOURCE_TIMEOUT_SECONDS` sets outbound request timeout.
- `CHIMERA_MCP_RESOURCE_AUTHORIZATION` optionally sets an `Authorization` header for the MCP gateway.
- `CHIMERA_OPENCLAW_SIGNING_SECRET` configures the HMAC secret used for OpenClaw status publication signatures.
- If the live adapter fails or returns blank payloads, runtime falls back to deterministic static resource payloads.
- If OpenClaw publication fails, orchestration continues and the failed attempt is retained in in-memory publisher audit history.

## Next Expansion

- Expand auth provider support (OIDC/JWT-backed key issuance).
- Expand distributed throughput/load validation (`docs/load_validation.md`) into sustained benchmark automation.
- Add on-call dashboard alerts aligned with replay governance runbook (`docs/replay_governance_runbook.md`).
- Expand telemetry with latency/SLO distributions and per-worker breakdowns.
- Emit structured audit events to centralized observability pipelines.
