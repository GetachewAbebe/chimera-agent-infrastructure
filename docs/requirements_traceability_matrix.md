# Requirements Traceability Matrix

Last updated: 2026-03-12

Status legend:
- `Done`: requirement is implemented and evidenced in repo.
- `Partial`: baseline exists but full requirement is not complete.
- `Gap`: requirement is not implemented in this repo yet.
- `External`: required submission artifact that is outside source code.

## 1) Challenge Document Traceability

Source: `Project Chimera_ The Agentic Infrastructure Challenge.docx`

| Requirement | Status | Evidence | Gap / Next Action |
|---|---|---|---|
| Day 1 research summary covers a16z, OpenClaw, MoltBook, SRS + answers 2 analysis questions | Done | `research/research_summary.md` | Keep this as Day 1 report core content |
| Domain architecture strategy includes agent pattern, HITL, DB strategy, Mermaid | Done | `research/architecture_strategy.md` | None |
| Java 21+ environment with Maven/Gradle and dependency tree | Done | `pom.xml`, `.github/workflows/main.yml` | None |
| Connect Tenx MCP Sense and configure IDE MCP | Partial | `.cursor/mcp.json`, `.vscode/mcp.json`, `.github/copilot-instructions.md` | Need authenticated connection proof (screenshots/logs) |
| Master specs in `specs/` (`_meta`, `functional`, `technical`, `openclaw_integration`) | Done | `specs/_meta.md`, `specs/functional.md`, `specs/technical.md`, `specs/openclaw_integration.md` | None |
| Rules file with project context, prime directive, Java directives, traceability | Done | `.cursor/rules/agent.mdc` | None |
| Tooling strategy distinguishes Dev MCP vs Runtime Skills | Done | `research/tooling_strategy.md` | None |
| At least 2 runtime skills with formal I/O contracts | Done | `skills/skill_download_youtube/README.md`, `skills/skill_transcribe_audio/README.md`, `skills/README.md` | Runtime implementations still pending |
| TDD contracts (`trendFetcherTest`, `skillsInterfaceTest`) | Done | `tests/org/chimera/tests/trendFetcherTest.java`, `tests/org/chimera/tests/skillsInterfaceTest.java` | Preserve red->green story in Loom demo |
| Automation via task runner (`make setup`, `make test`, `make lint`) | Done | `Makefile` | None |
| CI pipeline runs quality/test on push/PR | Done | `.github/workflows/main.yml` | None |
| AI review policy (`.coderabbit.yaml`) checks spec/thread-safety/security | Done | `.coderabbit.yaml` | None |
| Containerization (`Dockerfile`) | Done | `Dockerfile`, `docker-compose.yml`, `Makefile` (`docker-build`, `docker-test`, `docker-up`) | Run and capture local container smoke proof |
| Spec-check script (`make spec-check`) | Done | `scripts/spec_check.sh`, `Makefile` | None |
| Day 1 submission (Google Doc/PDF link) | External | Not stored in repo | Create and submit report link |
| Day 2 Loom (<=5 min) showing specs, TDD flow, agent context | External | Not stored in repo | Record Loom and include link |
| MCP telemetry active with same GitHub identity used for submission | External | Not stored in repo | Capture Tenx authenticated session evidence |

## 2) SRS Document Traceability

Source: `Project Chimera SRS Document_ Autonomous Influencer Network.docx`

| SRS Requirement Group | Status | Evidence | Gap / Next Action |
|---|---|---|---|
| FR 1.x Cognitive Core + `SOUL.md` persona + hierarchical memory (Redis + Weaviate) | Partial | `src/main/resources/soul/SOUL.md`, `src/main/java/org/chimera/cognitive/AgentPersona.java`, `src/main/java/org/chimera/cognitive/SoulMarkdownPersonaLoader.java`, `src/main/java/org/chimera/cognitive/ClasspathSoulPersonaLoader.java`, `src/main/java/org/chimera/cognitive/CognitiveContextAssembler.java`, `src/main/java/org/chimera/cognitive/InMemoryMemoryRecall.java`, `src/main/java/org/chimera/app/ChimeraApplication.java`, `src/test/java/org/chimera/cognitive/SoulMarkdownPersonaLoaderTest.java`, `src/test/java/org/chimera/cognitive/CognitiveContextAssemblerTest.java`, `src/test/java/org/chimera/cognitive/ClasspathSoulPersonaLoaderTest.java` | Replace in-memory memory recall with Redis + Weaviate-backed semantic retrieval |
| FR 2.x Perception system (MCP resource polling, semantic filtering, trend spotting) | Done | `src/main/java/org/chimera/perception/McpPerceptionService.java`, `src/main/java/org/chimera/mcp/HttpMcpResourceClient.java`, `src/main/java/org/chimera/planner/PlannerService.java`, `src/main/java/org/chimera/persistence/TrendSignalRepository.java`, `src/main/java/org/chimera/persistence/JdbcTrendSignalRepository.java`, `src/main/resources/db/migration/V7__api_trend_signals.sql`, `src/main/java/org/chimera/api/TelemetryApiService.java`, `src/test/java/org/chimera/tests/perception/McpPerceptionServiceTest.java`, `src/test/java/org/chimera/tests/planner/PlannerServiceTest.java`, `src/test/java/org/chimera/tests/mcp/HttpMcpResourceClientTest.java`, `src/test/java/org/chimera/tests/persistence/JdbcTrendSignalRepositoryTest.java` | Validate adapter against authenticated external MCP runtime and capture submission proof |
| FR 3.x Creative engine (text/image/video tool orchestration + consistency lock) | Partial | `src/main/java/org/chimera/creative/CreativeEngineService.java`, `src/main/java/org/chimera/creative/CreativeComposition.java`, `src/main/java/org/chimera/worker/WorkerService.java`, `src/main/java/org/chimera/judge/JudgeService.java`, `src/test/java/org/chimera/tests/creative/CreativeEngineServiceTest.java`, `src/test/java/org/chimera/tests/worker/WorkerServiceTest.java`, `src/test/java/org/chimera/tests/judge/JudgeServiceTest.java`, `src/test/java/org/chimera/tests/orchestrator/TaskOrchestratorServiceTest.java` | Add production-grade media moderation + visual similarity MCP adapters and artifact retention pipeline |
| FR 4.x Social action loop via MCP tools only | Done | `src/main/java/org/chimera/action/SocialPublishingService.java`, `src/main/java/org/chimera/worker/WorkerService.java`, `src/test/java/org/chimera/tests/action/SocialPublishingServiceTest.java`, `src/test/java/org/chimera/tests/worker/WorkerServiceTest.java` | Add platform-specific failure analytics dashboards |
| FR 5.x Agentic commerce (non-custodial wallet + autonomous tx + budget governance) | Partial | `src/main/java/org/chimera/wallet/CoinbaseAgentKitWalletProvider.java`, `src/main/java/org/chimera/wallet/WalletExecutionService.java`, `src/main/java/org/chimera/persistence/WalletLedgerRepository.java`, `src/main/java/org/chimera/persistence/JdbcWalletLedgerRepository.java`, `src/main/java/org/chimera/orchestrator/TaskOrchestratorService.java`, `src/main/java/org/chimera/api/TelemetryApiService.java`, `src/test/java/org/chimera/tests/wallet/CoinbaseAgentKitWalletProviderTest.java`, `src/test/java/org/chimera/tests/wallet/WalletExecutionServiceTest.java` | Execute live Coinbase runtime and capture signed-request telemetry evidence |
| FR 6.x Planner-Worker-Judge architecture + queue orchestration + OCC | Partial | `src/main/java/org/chimera/planner/PlannerService.java`, `src/main/java/org/chimera/worker/WorkerService.java`, `src/main/java/org/chimera/judge/JudgeService.java`, `src/main/java/org/chimera/orchestrator/TaskOrchestratorService.java`, `src/main/java/org/chimera/api/DeadLetterApiService.java`, `src/main/java/org/chimera/persistence/DeadLetterReplayAuditRepository.java`, `src/main/java/org/chimera/persistence/JdbcDeadLetterReplayAuditRepository.java`, `src/main/resources/db/migration/V6__api_dead_letter_replay_audit.sql`, `src/main/java/org/chimera/orchestrator/InMemoryQueueGovernanceMetrics.java`, `src/main/java/org/chimera/infrastructure/queue/RedisTaskQueuePort.java`, `src/main/java/org/chimera/infrastructure/queue/RedisUuidQueuePort.java`, `src/test/java/org/chimera/tests/orchestrator/TaskOrchestratorServiceTest.java`, `src/test/java/org/chimera/tests/orchestrator/InMemoryQueueGovernanceMetricsTest.java`, `src/test/java/org/chimera/tests/api/DeadLetterApiServiceTest.java`, `src/test/java/org/chimera/tests/persistence/JdbcDeadLetterReplayAuditRepositoryTest.java`, `src/test/java/org/chimera/tests/worker/WorkerServiceLoadTest.java`, `scripts/load_validation.sh`, `scripts/sustained_benchmark.sh`, `scripts/redis_cluster_stress.sh`, `frontend/src/App.tsx`, `.github/workflows/main.yml`, `docs/replay_governance_runbook.md`, `docs/redis_cluster_stress_profile.md` | Run Redis-cluster profile and publish benchmark artifacts |
| NFR 1.x Confidence + escalation + sensitive topic mandatory HITL | Partial | Confidence thresholds in `JudgeService`; sensitive-domain classifier in `src/main/java/org/chimera/security/SensitiveTopicClassifier.java`; enforcement in `src/main/java/org/chimera/orchestrator/TaskOrchestratorService.java`; review endpoints in `ChimeraHttpServer`; tests in `src/test/java/org/chimera/security/SensitiveTopicClassifierTest.java` and `src/test/java/org/chimera/tests/orchestrator/TaskOrchestratorServiceTest.java` | Add persistent reviewer prioritization UI and policy tuning workflow |
| NFR 2.x Transparency (AI disclosure labeling + honesty directive) | Done | Disclosure enforcement in `src/main/java/org/chimera/action/SocialPublishingService.java`; honesty directive in `src/main/java/org/chimera/worker/WorkerService.java`; tests in `src/test/java/org/chimera/tests/action/SocialPublishingServiceTest.java` and `src/test/java/org/chimera/tests/worker/WorkerServiceTest.java` | Add platform-specific disclosure telemetry dashboards |
| NFR 3.x Scale and latency (1,000 agents; <=10s interaction) | Partial | Virtual-thread execution in `src/main/java/org/chimera/worker/WorkerService.java` and API server, plus baseline + sustained load validation in `src/test/java/org/chimera/tests/worker/WorkerServiceLoadTest.java`, `scripts/load_validation.sh`, `scripts/sustained_benchmark.sh`, `scripts/redis_cluster_stress.sh`, worker SLO telemetry in `src/main/java/org/chimera/api/TelemetryApiService.java` and `frontend/src/App.tsx`, and nightly benchmark workflow in `.github/workflows/main.yml` | Execute 1,000-agent benchmark profile and publish p95 SLO trendlines from real run artifacts |
| Interface requirements: full dashboard (fleet status, campaign composer, review queue) | Done | `frontend/src/App.tsx`, `frontend/src/api/chimeraClient.ts`, `src/main/java/org/chimera/api/TelemetryApiService.java`, `src/main/java/org/chimera/api/ChimeraHttpServer.java` (CORS + preflight for browser runtime), `src/test/java/org/chimera/tests/api/ChimeraHttpServerAuthTest.java` (`shouldRunEndToEndCampaignReviewReplayJourneyAcrossAuthModes`, `shouldHandleCorsPreflightWithoutAuthentication`), `frontend/tests/e2e/dashboard.spec.ts`, `frontend/tests/e2e/dashboard.live.spec.ts` | Add richer fleet-level drill-down views |

## 3) Tenx MCP Guide Traceability

Source: `Tenx MCP Analysis & IDE Connection Guide.docx`

| Tenx Requirement | Status | Evidence | Gap / Next Action |
|---|---|---|---|
| Cursor rules file exists (`.cursor/rules/agent.mdc`) | Done | `.cursor/rules/agent.mdc` | None |
| Cursor MCP config includes `tenxfeedbackanalytics` URL + headers | Done | `.cursor/mcp.json` | None |
| VS Code MCP config includes server + headers | Done | `.vscode/mcp.json` | None |
| VS Code copilot instructions file exists | Done | `.github/copilot-instructions.md` | None |
| MCP authenticated connection started with GitHub account | External | Not stored in repo | Capture screenshot/recording of connected state and tools visible |
| Tenx telemetry logs (“Passage of Time”, “Performance Schema”) being emitted | External | Not stored in repo | Validate in Tenx dashboard and capture proof |

## 4) Rubric Readiness Snapshot

Based on provided rubric categories and current repo state.

| Rubric Category | Current Readiness | Evidence | Highest-Impact Improvement |
|---|---|---|---|
| Research & Domain Analysis | Pro-ready | `research/research_summary.md` | Add final Day 1 report formatting for submission |
| Architectural Approach | Pro-ready | `research/architecture_strategy.md` | Add deployment diagram + sequence diagram |
| Agent Rules File | Pro-ready | `.cursor/rules/agent.mdc` | None |
| Containerization | Pro-ready | `Dockerfile`, `docker-compose.yml`, `Makefile` | Add container CI smoke test |
| Automation (Task Runner) | Pro-ready | `Makefile` | None |
| CI/CD & Governance Pipeline | Pro-ready | `.github/workflows/main.yml`, `.coderabbit.yaml` | Add status badges + branch protections |
| Testing (TDD) | Average | `src/test/java/**` | Preserve explicit red-phase evidence in commit history/video |
| Java Data Modeling (Immutability) | Pro-ready | `src/main/java/org/chimera/model/*.java` (`record`) | None |
| Swarm Concurrency | Pro-ready | Virtual threads in Worker/API + queue retry/dead-letter orchestration + load validation (`WorkerServiceLoadTest`, `scripts/load_validation.sh`, `scripts/sustained_benchmark.sh`) | Add Redis-cluster benchmark coverage |
| Repository Documentation | Pro-ready | `README.md`, `docs/**`, `specs/**` | Add one-click onboarding script and architecture diagram index |
| Agentic Trajectory & Growth | Pro-ready | `docs/agentic_trajectory.md` | Tie roadmap items to dated milestones |
| DB & Data Management | Above Average | JDBC repos, migrations, rate-limit state, wallet ledger persistence (`api_wallet_ledger`) | Implement vector memory integration and retention governance policies |
| Backend | Pro-ready | API/auth/rate-limit/persistence stack plus planner-worker-judge orchestration, distributed queue runtime, retry/dead-letter policy, replay workflow with persistent governance audit + runbook, wallet ledger persistence, telemetry, CORS browser support, and load validation tooling with sustained benchmark automation | Add runtime observability hardening |
| Frontend | Pro-ready | `frontend/src/App.tsx`, `frontend/src/api/chimeraClient.ts`, `frontend/src/components/*` with live financial and queue resiliency telemetry cards plus mocked and live browser E2E flows in `frontend/tests/e2e/dashboard.spec.ts` and `frontend/tests/e2e/dashboard.live.spec.ts` | Add richer fleet-level drill-down views |
| Rule Creation (Agent Intent) | Pro-ready | `.cursor/rules/agent.mdc`, `docs/rule_creation_blueprint.md` | Add rule tests/checker |
| Security | Pro-ready | API key/JWT/JWKS/rate-limit/budget guard, sensitive-topic classifier escalation, disclosure enforcement, honesty directive handling | Add external secrets-manager runtime integration |
| Acceptance Criteria | Average | `specs/acceptance_criteria.md`, tests | Expand criteria-to-test coverage for all major SRS modules |
| MCP Configuration | Pro-ready | `.cursor/mcp.json`, `.vscode/mcp.json` | Add connection verification artifact |
| Agent Skills Structure | Pro-ready | `skills/**`, `src/main/java/org/chimera/skills/RuntimeSkillGateway.java`, `src/main/java/org/chimera/skills/DownloadYoutubeSkill.java`, `src/main/java/org/chimera/skills/TranscribeAudioSkill.java`, `tests/org/chimera/tests/skillsInterfaceTest.java` | Add external tool-backed runtime adapters for production media workloads |
| Commit Progression & Git Hygiene | Gap | `git log` shows single initial commit | Rebuild commit history in logical milestones before final submission |

## 5) Immediate Execution Order (to close top gaps)

1. Add proof artifacts for Tenx connection, Day 1 report link, and Loom walkthrough.
2. Capture nightly benchmark trend charts in submission-ready screenshots.
3. Attach branch protections/status badges to reinforce governance posture.
