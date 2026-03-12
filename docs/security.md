# Security Baseline

## Authentication and Authorization

- Tenant isolation at the data model and API boundary.
- Role separation: operator, reviewer, viewer.
- API requests require `X-Tenant-Id` and `X-Role` plus one auth method:
  - `X-Api-Key`
  - `Authorization: Bearer <jwt>`
- API keys are tenant-bound and can only assume explicitly allowed roles.
- JWT claims are validated for signature, expiry, tenant, and allowed roles.
- Bearer validation supports:
  - HS256 via `CHIMERA_JWT_HS256_SECRET`
  - RS256 via remote JWKS (`CHIMERA_JWT_JWKS_URL`) or local JWKS (`CHIMERA_JWT_JWKS_PATH`).
- RS256 JWKS validation uses `kid` key resolution, periodic reload, and fail-closed behavior on refresh/load errors.
- Optional `iss` and `aud` enforcement via `CHIMERA_JWT_ISSUER` and `CHIMERA_JWT_AUDIENCE`.
- Authorization matrix is endpoint-aware (campaign creation vs. review actions vs. task listing).
- All auth failures return structured API errors with request IDs for traceability.

## Secrets Management

- Secrets from environment variables or external secret manager.
- No plaintext secrets in git, logs, or test fixtures.

## Rate Limiting and Containment

- Campaign and review write endpoints are rate-limited per tenant and path (`429 rate_limited`).
- Primary backend is Redis token-bucket throttling (`REDIS_URL`) for low-latency distributed limits.
- PostgreSQL mode uses shared JDBC state (`api_rate_limits`) as distributed fallback when Redis is unavailable.
- In local/no-DB mode, write limits use in-memory counters.
- MCP adapters enforce downstream platform rate limits.
- Circuit-breakers for failing external integrations.
- Budget governor blocks suspicious or excessive spend.

## Auditability

- API responses include `X-Request-Id` for correlation.
- Server logs emit structured request audit entries with method, path, status, tenant, and role.

## Sensitive Domain Handling

Always escalate to HITL for:

- Political statements
- Health advice
- Financial advice
- Legal claims

Implementation status:

- Sensitive-topic escalation is enforced in orchestration policy using
  `org.chimera.security.SensitiveTopicClassifier`.
- Disclosure containment is enforced in `SocialPublishingService`, which upgrades `NONE` disclosure
  requests to `AUTOMATED` before MCP tool execution.
- AI-identity inquiries are handled by an honesty directive in worker reply generation.
