# Final Submission Checklist

## Assignment Links

- [ ] Day 1 report link submitted (direct document URL)
- [ ] Day 2 Loom video link submitted (<= 5 minutes)
- [ ] Day 2 final GitHub repository link submitted

## Mandatory Repository Artifacts

- [x] `specs/` complete (`_meta`, `functional`, `technical`, `openclaw_integration`, `acceptance_criteria`)
- [x] `skills/` structure with at least 2 skills and I/O contracts
- [x] Rules file (`.cursor/rules/agent.mdc`)
- [x] Automation (`Makefile`: setup/test/lint/spec-check)
- [x] CI workflow (`.github/workflows/main.yml`)
- [x] AI governance policy (`.coderabbit.yaml`)
- [x] Containerization (`Dockerfile`, `docker-compose.yml`)
- [x] Frontend implementation and E2E tests (`frontend/tests/e2e/*.spec.ts`)

## Rubric Evidence Index

- Research & Domain Analysis:
  - `research/research_summary.md`
  - `reports/day1_research_report.md`
- Architectural Approach:
  - `research/architecture_strategy.md`
  - `docs/architecture.md`
- DB & Data Management:
  - `docs/db_data_management.md`
  - `src/main/resources/db/migration/*`
- Backend:
  - `src/main/java/org/chimera/**`
- Frontend:
  - `frontend/src/**`
  - `docs/frontend.md`
- Rule Creation / Agent Rules:
  - `.cursor/rules/agent.mdc`
  - `docs/rule_creation_blueprint.md`
- Security:
  - `docs/security.md`
  - `src/main/java/org/chimera/api/*Auth*.java`
- Acceptance Criteria:
  - `specs/acceptance_criteria.md`
- MCP Configuration:
  - `.cursor/mcp.json`
  - `.vscode/mcp.json`
- Agent Skills Structure:
  - `skills/**`
  - `src/test/java/org/chimera/tests/skillsInterfaceTest.java`
- CI/CD & Governance:
  - `.github/workflows/main.yml`
  - `.coderabbit.yaml`
- Testing (TDD):
  - `src/test/java/org/chimera/tests/**`
- Swarm Concurrency:
  - `src/main/java/org/chimera/worker/WorkerService.java`
  - `scripts/load_validation.sh`
  - `scripts/sustained_benchmark.sh`
- Repository Documentation:
  - `README.md`
  - `docs/**`

## Tenx MCP Evidence (External)

- [ ] Screenshot/video of authenticated Tenx MCP connection in IDE
- [ ] Screenshot of tools visible in agent mode
- [ ] Screenshot/log showing Tenx telemetry events
