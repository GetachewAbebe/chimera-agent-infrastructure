# OpenClaw Integration Plan

## Objective
Publish Chimera agent availability/status into an OpenClaw-compatible agent network while preserving tenant isolation and governance boundaries.

## Status Signal Model

Each agent publishes:

- `agent_id`
- `tenant_id`
- `availability`: `online | busy | paused | offline`
- `capabilities`: list of MCP tools and skill signatures
- `safety_level`: `autonomous | supervised | restricted`
- `updated_at`

## Proposed MCP Resource and Tool

- Resource: `openclaw://agents/{agent_id}/status`
- Tool: `openclaw.publish_status`

## Publication Policy

- Publish on state transitions (`planning`, `working`, `judging`, `sleeping`).
- Debounce burst updates to avoid protocol spam.
- Block publication if policy checks fail (missing disclosure or unresolved high-risk escalation).

## Governance

- Signed status payloads to prevent spoofing.
- Tenant-scoped keys and rotation every 90 days.
- Full audit logs for status publication attempts and outcomes.
