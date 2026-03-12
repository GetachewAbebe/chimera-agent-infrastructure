# Dead-Letter Replay Governance Runbook

This runbook defines how operators and reviewers handle replay-governed tasks and audit policy outcomes.

## Replay Policy Controls

Replay policy is enforced in `DeadLetterApiService`:

- cooldown between accepted replays of the same task
- max accepted replays per task per UTC day
- accepted and rejected replay decisions audited with reason

Runtime controls:

- `CHIMERA_REPLAY_COOLDOWN_SECONDS` (default `300`)
- `CHIMERA_REPLAY_MAX_PER_TASK_PER_DAY` (default `3`)

## API Behavior

Endpoint:

- `POST /api/dead-letter/{taskId}/replay`

Common responses:

- `200`: replay accepted, task returned to `PENDING`
- `404`: task not found for tenant scope
- `409`: task state not replay-eligible (`REJECTED` required)
- `429` + `replay_rate_limited`: per-day task replay cap exceeded
- `429` + `replay_cooldown_active`: cooldown window still active

## Audit Table

Replay events are stored in:

- `api_dead_letter_replay_audit`

Columns:

- `event_id`
- `tenant_id`
- `task_id`
- `accepted`
- `reason`
- `occurred_at`

## Operator Procedures

1. Validate request context:
   - tenant is correct
   - task is currently `REJECTED`
2. Attempt replay from UI or API.
3. If replay blocked, inspect most recent audit events and reason.
4. Apply incident policy:
   - `cooldown_active`: retry after window expires
   - `daily_limit_exceeded`: defer to next UTC day or escalate
5. Record exception handling decisions in incident notes.

## SQL Queries

Latest replay events for tenant:

```sql
SELECT tenant_id, task_id, accepted, reason, occurred_at
FROM api_dead_letter_replay_audit
WHERE tenant_id = :tenant_id
ORDER BY occurred_at DESC
LIMIT 100;
```

Accepted replay count for task today (UTC):

```sql
SELECT COUNT(*) AS accepted_replays_today
FROM api_dead_letter_replay_audit
WHERE tenant_id = :tenant_id
  AND task_id = :task_id
  AND accepted = TRUE
  AND occurred_at >= date_trunc('day', now() AT TIME ZONE 'UTC')
  AND occurred_at < date_trunc('day', now() AT TIME ZONE 'UTC') + interval '1 day';
```

Tasks with repeated replay denials in last 24h:

```sql
SELECT tenant_id, task_id, reason, COUNT(*) AS denied_count
FROM api_dead_letter_replay_audit
WHERE accepted = FALSE
  AND occurred_at >= now() - interval '24 hours'
GROUP BY tenant_id, task_id, reason
ORDER BY denied_count DESC;
```

## Escalation Guidance

- Escalate to reviewer lead when a task repeatedly hits replay limits.
- Escalate to platform engineering when audit writes fail or replay endpoint returns unexpected 5xx.
- For emergency policy tuning, adjust env vars and restart service in a controlled change window.
