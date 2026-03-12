# Architecture Overview

Project Chimera follows a governed swarm architecture:

1. Planner decomposes strategic goals into task contracts.
2. Worker pool executes tasks using MCP integrations.
3. Judge validates outputs and enforces governance policies.
4. HITL receives escalations for medium confidence and sensitive domains.

## Key Design Decisions

- Decouple orchestration from third-party API churn by using MCP.
- Use immutable Java records for deterministic, thread-safe data exchange.
- Apply optimistic concurrency control to protect global state integrity.

## Runtime Topology

- Stateless services for planner/worker/judge
- Redis queue for task and review events
- PostgreSQL for transactional state
- Vector store for semantic memory retrieval
