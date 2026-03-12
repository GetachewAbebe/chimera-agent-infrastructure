# Day 2 Loom Script (<= 5 Minutes)

## 0:00 - 0:40 Project Framing
- Open `README.md`.
- State objective: spec-driven, governed agentic infrastructure for autonomous influencer operations.
- Mention stack: Java 21, virtual threads, React frontend, MCP-oriented integration boundaries.

## 0:40 - 1:40 Spec-First Structure
- Show `specs/`:
  - `_meta.md`
  - `functional.md`
  - `technical.md`
  - `openclaw_integration.md`
  - `acceptance_criteria.md`
- Explain: implementation and tests map back to these specs.

## 1:40 - 2:30 TDD and Quality Gates
- Run:
  - `make lint`
  - `make spec-check`
  - `make test`
- Highlight:
  - `trendFetcherTest.java`
  - `skillsInterfaceTest.java`
  - API end-to-end auth journey in `ChimeraHttpServerAuthTest`.

## 2:30 - 3:20 Agent Context and Rules
- Open `.cursor/rules/agent.mdc`.
- Call out mandatory directives:
  - check `specs/` before code generation
  - Java 21 idioms and immutable records
  - explain plan before code changes
  - maintain MCP boundaries

## 3:20 - 4:20 Runtime Demo (API + Frontend)
- Run API:
  - `PORT=8080 make run`
- Run frontend:
  - `cd frontend && npm run dev`
- In UI:
  - create a campaign
  - show review queue
  - reject then replay a task
  - show telemetry cards

## 4:20 - 5:00 Governance and Automation
- Show:
  - `.github/workflows/main.yml` (quality pipeline + nightly benchmark job)
  - `scripts/load_validation.sh`
  - `scripts/sustained_benchmark.sh`
- Mention replay governance runbook:
  - `docs/replay_governance_runbook.md`

## Closing
- Share repo URL.
- Share Day 1 report link.
- Confirm Tenx MCP connection and telemetry visibility.
