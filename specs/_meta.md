# Project Metadata and Constraints

## Vision
Build a governed autonomous influencer infrastructure where agent intent is defined by ratified specs, executed through MCP interfaces, and enforced by Planner/Worker/Judge controls.

## Prime Directives

1. Never implement behavior before contract and acceptance criteria are defined.
2. All external interactions must go through MCP tools/resources.
3. Human review is mandatory for sensitive topics and medium-confidence outputs.
4. Financial actions must pass budget governance checks.

## System Constraints

- Language/runtime: Java 21+
- Concurrency: Virtual threads for high parallelism worker execution
- Persistence:
  - PostgreSQL (transactional)
  - Redis (queues/cache)
  - Vector DB (semantic memory references)
- Security:
  - Secrets only via environment variables / secret manager
  - No private keys in repo or logs
- Compliance:
  - AI-generated content disclosure flags must be supported

## Out of Scope (Current Phase)

- Full production social-platform adapters
- Real blockchain transfer execution in this baseline
- Full UI implementation (contracts/spec and component baseline included)
