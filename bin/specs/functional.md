# Functional Specification

## User Stories

## Orchestrator and Campaigning

- As a Network Operator, I want to submit campaign goals in natural language so the planner can decompose tasks.
- As a Network Operator, I want visibility into queue depth, agent state, and escalation workload.

## Planner / Worker / Judge

- As a Planner Agent, I need to convert strategic goals into atomic tasks with explicit acceptance criteria.
- As a Worker Agent, I need to execute a task with MCP tools and return artifacts plus confidence metadata.
- As a Judge Agent, I need to approve, reject, or escalate outputs according to policy.

## Human-in-the-Loop

- As a Human Reviewer, I want medium-confidence and sensitive outputs routed into a review queue.
- As a Human Reviewer, I want approve/reject actions that are auditable.

## Skills and MCP

- As a runtime system, I need formalized skill interfaces with deterministic input/output contracts.
- As a developer, I need MCP server configuration to be explicit and versioned.

## Financial Governance

- As a CFO Judge, I need to block transactions that exceed daily spend limits.
- As a compliance control, I need every transaction attempt to be logged with reason codes.

## Acceptance Signals (High-Level)

- Planner emits valid task schemas.
- Worker handles task execution lifecycle.
- Judge enforces confidence thresholds and OCC checks.
- TDD contract tests exist before implementation completion.
