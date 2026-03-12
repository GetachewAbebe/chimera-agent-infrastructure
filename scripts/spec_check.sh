#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "specs/_meta.md"
  "specs/functional.md"
  "specs/technical.md"
  "specs/openclaw_integration.md"
  "specs/acceptance_criteria.md"
  "src/main/resources/openapi.yaml"
  "src/main/resources/soul/SOUL.md"
  "src/main/resources/db/migration/V4__api_rate_limits.sql"
  "src/main/resources/db/migration/V5__api_wallet_ledger.sql"
  "src/main/resources/db/migration/V6__api_dead_letter_replay_audit.sql"
  "src/main/resources/db/migration/V7__api_trend_signals.sql"
  "docs/load_validation.md"
  "docs/redis_cluster_stress_profile.md"
  "docs/replay_governance_runbook.md"
  "scripts/load_validation.sh"
  "scripts/sustained_benchmark.sh"
  "scripts/redis_cluster_stress.sh"
  "frontend/playwright.config.ts"
  "frontend/playwright.live.config.ts"
  "frontend/tests/e2e/dashboard.spec.ts"
  "frontend/tests/e2e/dashboard.live.spec.ts"
  ".cursor/rules/agent.mdc"
  "Makefile"
)

missing=0
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required spec artifact: $file"
    missing=1
  fi
done

if ! rg -q "NEVER generate code without checking specs/ first" .cursor/rules/agent.mdc; then
  echo "Agent rule directive missing from .cursor/rules/agent.mdc"
  missing=1
fi

if ! rg -q "Executors.newVirtualThreadPerTaskExecutor" src/main/java/org/chimera/worker/WorkerService.java; then
  echo "Swarm concurrency requirement missing in WorkerService"
  missing=1
fi

if [[ ! -f src/test/java/org/chimera/tests/worker/WorkerServiceLoadTest.java ]]; then
  echo "Worker load validation test missing"
  missing=1
fi

if ! rg -q "X-Tenant-Id" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "Tenant header enforcement missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "X-Api-Key" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "API key enforcement missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "Authorization" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "Bearer authorization header support missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "ENV_JWT_JWKS_PATH" src/main/java/org/chimera/api/JwtAuthService.java; then
  echo "JWKS bearer validation support missing in JwtAuthService"
  missing=1
fi

if ! rg -q "ENV_JWT_JWKS_URL" src/main/java/org/chimera/api/JwtAuthService.java; then
  echo "Remote JWKS bearer validation support missing in JwtAuthService"
  missing=1
fi

if ! rg -q "JdbcRequestRateLimiter" src/main/java/org/chimera/persistence/PersistenceBootstrap.java; then
  echo "Distributed JDBC rate limiter bootstrap missing in PersistenceBootstrap"
  missing=1
fi

if ! rg -q "RedisRequestRateLimiter" src/main/java/org/chimera/persistence/PersistenceBootstrap.java; then
  echo "Redis token-bucket rate limiter bootstrap missing in PersistenceBootstrap"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/perception/McpPerceptionService.java ]]; then
  echo "MCP perception service missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/mcp/HttpMcpResourceClient.java ]]; then
  echo "HTTP MCP resource adapter missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/cognitive/AgentPersona.java ]]; then
  echo "Agent persona model missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/cognitive/CognitiveContextAssembler.java ]]; then
  echo "Cognitive context assembler missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/skills/RuntimeSkillGateway.java ]]; then
  echo "Runtime skill gateway missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/skills/DownloadYoutubeSkill.java ]]; then
  echo "Download YouTube runtime skill missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/skills/TranscribeAudioSkill.java ]]; then
  echo "Transcribe audio runtime skill missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/action/SocialPublishingService.java ]]; then
  echo "Social publishing MCP action service missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/creative/CreativeEngineService.java ]]; then
  echo "Creative engine service missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/orchestrator/TaskOrchestratorService.java ]]; then
  echo "Task orchestrator service missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/wallet/CoinbaseAgentKitWalletProvider.java ]]; then
  echo "Coinbase wallet provider missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/security/SecretProvider.java ]]; then
  echo "Secret provider contract missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/security/SensitiveTopicClassifier.java ]]; then
  echo "Sensitive topic classifier missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/WalletLedgerRepository.java ]]; then
  echo "Wallet ledger repository contract missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/JdbcWalletLedgerRepository.java ]]; then
  echo "JDBC wallet ledger repository implementation missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/TrendSignalRepository.java ]]; then
  echo "Trend signal repository contract missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/JdbcTrendSignalRepository.java ]]; then
  echo "JDBC trend signal repository implementation missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/infrastructure/queue/RedisTaskQueuePort.java ]]; then
  echo "Redis task queue port missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/infrastructure/queue/RedisUuidQueuePort.java ]]; then
  echo "Redis review queue port missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/orchestrator/QueueGovernanceMetrics.java ]]; then
  echo "Queue governance metrics contract missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/orchestrator/InMemoryQueueGovernanceMetrics.java ]]; then
  echo "In-memory queue governance metrics implementation missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/api/DeadLetterApiService.java ]]; then
  echo "Dead-letter replay API service missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/DeadLetterReplayAuditRepository.java ]]; then
  echo "Replay audit repository contract missing"
  missing=1
fi

if [[ ! -f src/main/java/org/chimera/persistence/JdbcDeadLetterReplayAuditRepository.java ]]; then
  echo "JDBC replay audit repository implementation missing"
  missing=1
fi

if ! rg -q "rate_limited" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "Write-path rate limiting missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "/api/telemetry" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "Telemetry endpoint missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "/api/dead-letter" src/main/java/org/chimera/api/ChimeraHttpServer.java; then
  echo "Dead-letter replay endpoint missing in ChimeraHttpServer"
  missing=1
fi

if ! rg -q "shouldRunEndToEndCampaignReviewReplayJourneyAcrossAuthModes" src/test/java/org/chimera/tests/api/ChimeraHttpServerAuthTest.java; then
  echo "HTTP API end-to-end journey test missing"
  missing=1
fi

if ! rg -q "\"test:e2e\"" frontend/package.json; then
  echo "Frontend Playwright test script missing in frontend package.json"
  missing=1
fi

if ! rg -q "\"test:e2e:live\"" frontend/package.json; then
  echo "Frontend live Playwright test script missing in frontend package.json"
  missing=1
fi

if ! rg -q "name: X-Api-Key" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing X-Api-Key parameter"
  missing=1
fi

if ! rg -q "'429':" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing rate-limit response definition"
  missing=1
fi

if ! rg -q "name: Authorization" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing Authorization bearer parameter"
  missing=1
fi

if ! rg -q "/api/telemetry" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing telemetry endpoint"
  missing=1
fi

if ! rg -q "/api/dead-letter/\\{taskId\\}/replay" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing dead-letter replay endpoint"
  missing=1
fi

if ! rg -q "todaySpendUsd" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing wallet spend telemetry fields"
  missing=1
fi

if ! rg -q "deadLetterQueueDepth" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing dead-letter queue telemetry fields"
  missing=1
fi

if ! rg -q "workerP95LatencyMs" src/main/resources/openapi.yaml; then
  echo "OpenAPI contract missing worker latency telemetry fields"
  missing=1
fi

if ! rg -q "CHIMERA_QUEUE_MAX_RETRIES" src/main/java/org/chimera/app/ChimeraApplication.java; then
  echo "Queue retry runtime configuration missing in application bootstrap"
  missing=1
fi

if ! rg -q "CHIMERA_REPLAY_COOLDOWN_SECONDS" src/main/java/org/chimera/app/ChimeraApplication.java; then
  echo "Replay cooldown runtime configuration missing in application bootstrap"
  missing=1
fi

if ! rg -q "CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY" src/main/java/org/chimera/app/ChimeraApplication.java; then
  echo "Replay per-task daily limit runtime configuration missing in application bootstrap"
  missing=1
fi

if [[ "$missing" -ne 0 ]]; then
  echo "Spec check failed"
  exit 1
fi

echo "Spec check passed"
