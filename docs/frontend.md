# Frontend Contracts and UX Flow

## Primary Views

1. Fleet Status dashboard (agent state, spend, queue depth)
2. HITL review queue (approve/reject)
3. Campaign composer (goal -> task tree)

Implementation baseline:

- App shell: `frontend/src/App.tsx`
- API client: `frontend/src/api/chimeraClient.ts`
- Components: `frontend/src/components/*`
- Styles: `frontend/src/styles.css`

## ReviewCard Contract

Inputs:

- `generatedContent`: string | image URL
- `confidenceScore`: number
- `reasoningTrace`: string
- `taskId`: string

Actions:

- `POST /api/review/{taskId}/approve`
- `POST /api/review/{taskId}/reject`

## Dashboard Data Contracts

- `GET /api/tasks`
- `GET /api/telemetry`
- `POST /api/campaigns`
- `POST /api/review/{taskId}/approve`
- `POST /api/review/{taskId}/reject`
- `POST /api/dead-letter/{taskId}/replay`
  - May return `429` with `replay_rate_limited` or `replay_cooldown_active` when replay governance blocks rapid/manual replays.

Telemetry fields consumed by `App.tsx`:

- Queue/ops: `taskQueueDepth`, `reviewQueueDepth`, `deadLetterQueueDepth`, `queueBackend`, `statusCounts`
- Queue resilience: `retryAttemptsToday`, `deadLetteredTasksToday`
- Worker SLO: `workerP50LatencyMs`, `workerP95LatencyMs`, `successfulExecutionsToday`, `failedExecutionsToday`
- Wallet runtime: `walletProvider`
- Financial health: `dailyBudgetUsd`, `todaySpendUsd`, `remainingBudgetUsd`, `walletTransfersToday`, `spendDeltaVsYesterdayUsd`
- Perception health: `trendSignalsToday`, `topTrendTopicsToday`

Rules:

- Confidence badge colors:
  - Green: > 0.90
  - Yellow: 0.70 to 0.90
  - Red: < 0.70
- Red border when confidence < 0.80

## Frontend Browser E2E (Playwright)

Playwright suite:

- `frontend/tests/e2e/dashboard.spec.ts`
- `frontend/tests/e2e/dashboard.live.spec.ts`

Coverage includes:

- auth context persistence in local storage
- bearer token preference over API key in request headers
- campaign creation flow from UI form submission
- reviewer reject + operator replay path from UI actions
- replay cooldown error rendering in message panel
- live browser journey against real backend API (no route mocks)

Run locally:

```bash
cd frontend
npm run test:e2e:install
npm run test:e2e
npm run test:e2e:live
```

## Browser-to-API Connectivity

- Backend now supports CORS preflight and allows local frontend origins by default:
  - `http://localhost:5173`
  - `http://127.0.0.1:5173`
  - `http://localhost:4173`
  - `http://127.0.0.1:4173`
- Override with `CHIMERA_CORS_ALLOWED_ORIGINS` (comma-separated origins).
