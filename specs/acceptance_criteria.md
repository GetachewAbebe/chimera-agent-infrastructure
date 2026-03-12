# Acceptance Criteria

## AC-1 Planner

- Given a valid campaign goal, planner emits at least one task that matches the task schema.
- Each task includes explicit required MCP resources.

## AC-2 Worker

- Given a valid task, worker returns a task result with confidence score and reasoning trace.
- Worker execution is non-blocking and supports high parallelism (virtual-thread backed).

## AC-3 Judge Governance

- Judge rejects stale results when state version mismatches.
- Judge auto-approves only if confidence > 0.90 and no sensitive topic is detected.
- Judge escalates mandatory HITL for sensitive topics regardless of confidence.

## AC-4 Security and Budget

- Transaction tasks are rejected when projected spend exceeds daily budget limit.
- Secrets are never hardcoded in source control.
- Sensitive topics (politics/health/financial/legal) are escalated to HITL regardless of confidence.
- Social actions never publish with `disclosure_level=none`; disclosure is enforced as `automated` or `assisted`.
- AI-identity inquiries trigger explicit honesty disclosure in reply generation.

## AC-5 TDD Contracts

- `trendFetcherTest` and `skillsInterfaceTest` exist and are executable via Maven.
- Initial red-phase contracts are implemented and currently pass in this green-phase baseline.

## AC-6 Automation and CI

- `make setup`, `make lint`, `make spec-check`, and `make test` are defined.
- GitHub Actions runs governance checks on push and pull request.

## AC-7 Tenant and Role Security

- API endpoints require `X-Tenant-Id` and `X-Role`, and one auth mechanism:
  - `X-Api-Key`
  - `Authorization: Bearer <jwt>`
- API key tenant must match `X-Tenant-Id`; role must be allowed by that key.
- JWT tenant/role claims must match request tenant/role and must pass signature/expiry validation.
- RS256 JWTs must validate against JWKS `kid` (URL or file) and tolerate key rotation after JWKS refresh.
- If JWKS refresh fails, bearer auth must fail closed until keys are successfully reloaded.
- Campaign creation is restricted to `operator`.
- Review actions are restricted to `reviewer`.
- Task list and review operations are scoped to tenant-owned tasks only.
- Campaign and review write endpoints return `429` when tenant write rate is exceeded.
- In `REDIS_URL` mode, token-bucket counters are shared across instances with low-latency enforcement.
- If Redis is unavailable and PostgreSQL is configured, rate-limit counters remain shared via `api_rate_limits` (distributed consistency).
