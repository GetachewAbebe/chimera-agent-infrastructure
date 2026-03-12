# AGENTS.md - Project Chimera Governance Context

## Project Mission
Project Chimera builds a governed autonomous influencer infrastructure using spec-first development and MCP-based integrations.

## Global Rules

1. Read `specs/` before proposing or generating implementation code.
2. Preserve Planner -> Worker -> Judge boundaries.
3. Keep all external interactions behind MCP interfaces.
4. Enforce HITL and sensitive-topic escalation policy.
5. Never commit secrets or private keys.

## Java Engineering Standards

- Java 21 minimum
- Immutable DTOs as `record`
- Virtual threads for worker parallelism
- JUnit 5 for testing

## Governance Checks

- `make lint`
- `make spec-check`
- `make test`

## Security Expectations

- Budget checks for transaction tasks
- Rate limits in integration adapters
- Tenant data isolation and auditable decision traces
