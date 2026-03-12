import { useEffect, useMemo, useRef, useState } from "react";
import {
  ApiRequestError,
  approveTask,
  checkApiHealth,
  createCampaign,
  getTelemetry,
  listTasks,
  replayDeadLetterTask,
  rejectTask
} from "./api/chimeraClient";
import { CampaignComposer } from "./components/CampaignComposer";
import { MetricCard } from "./components/MetricCard";
import { ReviewCard } from "./components/ReviewCard";
import { TaskTable } from "./components/TaskTable";
import type { AuthConfig, Task, TelemetrySnapshot } from "./types/chimera";

const STORAGE_KEY = "chimera-console-auth";
const AUTO_REFRESH_INTERVAL_MS = 15_000;

type ConnectionState = "checking" | "live" | "degraded" | "offline";
type TelemetryMode = "live" | "derived";

type WorkerSummary = {
  workerId: string;
  totalTasks: number;
  activeTasks: number;
  reviewTasks: number;
  completedTasks: number;
  rejectedTasks: number;
  latestStatus: Task["status"];
  latestGoal: string;
  latestCreatedAt: string;
};

const defaultAuth: AuthConfig = {
  baseUrl: "http://localhost:8080",
  tenantId: "tenant-alpha",
  apiKey: "dev-tenant-alpha-key",
  bearerToken: ""
};

const statusOrder: Task["status"][] = [
  "PENDING",
  "IN_PROGRESS",
  "REVIEW",
  "ESCALATED",
  "COMPLETE",
  "REJECTED"
];

const loadAuth = (): AuthConfig => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return defaultAuth;
    }
    const parsed = JSON.parse(raw) as Partial<AuthConfig>;
    return {
      baseUrl: parsed.baseUrl ?? defaultAuth.baseUrl,
      tenantId: parsed.tenantId ?? defaultAuth.tenantId,
      apiKey: parsed.apiKey ?? defaultAuth.apiKey,
      bearerToken: parsed.bearerToken ?? defaultAuth.bearerToken
    };
  } catch {
    return defaultAuth;
  }
};

const confidenceFromStatus = (status: string): number => {
  switch (status) {
    case "ESCALATED":
      return 0.65;
    case "REVIEW":
      return 0.76;
    case "PENDING":
      return 0.73;
    case "IN_PROGRESS":
      return 0.81;
    case "COMPLETE":
      return 0.94;
    case "REJECTED":
      return 0.58;
    default:
      return 0.7;
  }
};

const formatUsd = (amount: number): string => `$${amount.toFixed(2)}`;

const formatDelta = (amount: number): string => {
  const sign = amount > 0 ? "+" : "";
  return `${sign}$${amount.toFixed(2)} vs yesterday`;
};

const formatTrendTopics = (topics: string[] | undefined): string => {
  if (!topics || topics.length === 0) {
    return "No strong signals yet";
  }
  return topics.slice(0, 2).join(", ");
};

const formatExecutionRate = (successCount: number | undefined, failedCount: number | undefined): string => {
  const successes = successCount ?? 0;
  const failures = failedCount ?? 0;
  const total = successes + failures;
  if (total === 0) {
    return "0%";
  }
  return `${Math.round((successes / total) * 100)}%`;
};

const formatSyncTime = (value: string | null): string => {
  if (!value) {
    return "Never";
  }
  return new Date(value).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  });
};

const truncate = (value: string, maxLength: number): string => {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 1)}...`;
};

const isActionableReviewTask = (task: Task): boolean =>
  task.status === "PENDING" || task.status === "REVIEW" || task.status === "ESCALATED";

const isGovernedReviewTask = (task: Task): boolean =>
  task.status === "REVIEW" || task.status === "ESCALATED";

const isActiveTask = (task: Task): boolean =>
  task.status === "PENDING" || task.status === "IN_PROGRESS";

const buildStatusCounts = (tasks: Task[]): Record<string, number> => {
  const counts: Record<string, number> = {};
  for (const status of statusOrder) {
    counts[status] = 0;
  }
  for (const task of tasks) {
    counts[task.status] = (counts[task.status] ?? 0) + 1;
  }
  return counts;
};

const buildWorkerSummaries = (tasks: Task[]): WorkerSummary[] => {
  const summaries = new Map<string, WorkerSummary>();

  for (const task of tasks) {
    const existing =
      summaries.get(task.assignedWorkerId) ??
      ({
        workerId: task.assignedWorkerId,
        totalTasks: 0,
        activeTasks: 0,
        reviewTasks: 0,
        completedTasks: 0,
        rejectedTasks: 0,
        latestStatus: task.status,
        latestGoal: task.context.goalDescription,
        latestCreatedAt: task.createdAt
      } satisfies WorkerSummary);

    existing.totalTasks += 1;
    if (isActiveTask(task)) {
      existing.activeTasks += 1;
    }
    if (isGovernedReviewTask(task)) {
      existing.reviewTasks += 1;
    }
    if (task.status === "COMPLETE") {
      existing.completedTasks += 1;
    }
    if (task.status === "REJECTED") {
      existing.rejectedTasks += 1;
    }

    if (new Date(task.createdAt).getTime() >= new Date(existing.latestCreatedAt).getTime()) {
      existing.latestCreatedAt = task.createdAt;
      existing.latestStatus = task.status;
      existing.latestGoal = task.context.goalDescription;
    }

    summaries.set(task.assignedWorkerId, existing);
  }

  return Array.from(summaries.values()).sort((left, right) => {
    if (right.totalTasks !== left.totalTasks) {
      return right.totalTasks - left.totalTasks;
    }
    return new Date(right.latestCreatedAt).getTime() - new Date(left.latestCreatedAt).getTime();
  });
};

const describeDashboardError = (
  auth: AuthConfig,
  error: unknown,
  apiReachable: boolean
): string => {
  if (error instanceof ApiRequestError) {
    if (error.status === 401 || error.status === 403) {
      return `${error.message} (HTTP ${error.status}). Verify the tenant, API key, or bearer token in the console header.`;
    }
    if (error.status === 429) {
      return `${error.message} (HTTP 429). The runtime accepted the request shape but throttled it. Retry after the current window resets.`;
    }
    return `${error.message} (HTTP ${error.status}).`;
  }

  if (error instanceof Error && /fetch/i.test(error.message)) {
    if (apiReachable) {
      return `Browser fetch failed while ${auth.baseUrl} answered /health. Verify the API base URL and CORS settings, then refresh.`;
    }
    return `Unable to reach ${auth.baseUrl}. Start the API with "PORT=8080 make run" and refresh the dashboard.`;
  }

  if (error instanceof Error) {
    return error.message;
  }

  if (apiReachable) {
    return "Dashboard request failed even though the API is reachable. Verify the current auth context and retry.";
  }
  return `Unable to reach ${auth.baseUrl}. Start the API with "PORT=8080 make run" and refresh the dashboard.`;
};

function App() {
  const [auth, setAuth] = useState<AuthConfig>(loadAuth);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [telemetry, setTelemetry] = useState<TelemetrySnapshot | null>(null);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [submittingCampaign, setSubmittingCampaign] = useState(false);
  const [reviewingTaskId, setReviewingTaskId] = useState<string | null>(null);
  const [replayingTaskId, setReplayingTaskId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [connectionState, setConnectionState] = useState<ConnectionState>("checking");
  const [telemetryMode, setTelemetryMode] = useState<TelemetryMode>("derived");
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastSyncAt, setLastSyncAt] = useState<string | null>(null);

  const authRef = useRef(auth);
  const lastSyncRef = useRef<string | null>(null);

  useEffect(() => {
    authRef.current = auth;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  }, [auth]);

  useEffect(() => {
    lastSyncRef.current = lastSyncAt;
  }, [lastSyncAt]);

  const refreshTasks = async (options?: { silent?: boolean }) => {
    const currentAuth = authRef.current;
    setLoadingTasks(true);
    setError("");
    if (!options?.silent) {
      setConnectionState("checking");
    }

    try {
      const latest = await listTasks(currentAuth);
      setTasks(latest);

      const syncedAt = new Date().toISOString();
      setLastSyncAt(syncedAt);
      setConnectionState("live");

      let nextNotice = `Loaded ${latest.length} task(s).`;

      try {
        const telemetrySnapshot = await getTelemetry(currentAuth);
        setTelemetry(telemetrySnapshot);
        setTelemetryMode("live");
      } catch {
        setTelemetry(null);
        setTelemetryMode("derived");
        setConnectionState("degraded");
        nextNotice = `Loaded ${latest.length} task(s). Live telemetry is unavailable, so the dashboard is using client-derived counters.`;
      }

      if (!options?.silent) {
        setNotice(nextNotice);
      }
    } catch (caught) {
      const apiReachable = await checkApiHealth(currentAuth.baseUrl).catch(() => false);
      setConnectionState(apiReachable ? "degraded" : "offline");
      setError(describeDashboardError(currentAuth, caught, apiReachable));

      if (lastSyncRef.current && !options?.silent) {
        setNotice("Showing the last successful snapshot while the connection recovers.");
      }
    } finally {
      setLoadingTasks(false);
    }
  };

  useEffect(() => {
    void refreshTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!autoRefresh) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void refreshTasks({ silent: true });
    }, AUTO_REFRESH_INTERVAL_MS);

    return () => window.clearInterval(intervalId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh]);

  const handleCampaignSubmit = async (payload: {
    goal: string;
    workerId: string;
    requiredResources: string[];
  }) => {
    setSubmittingCampaign(true);
    setError("");
    try {
      const created = await createCampaign(auth, payload);
      setNotice(`Created ${created.length} task(s) from campaign goal.`);
      await refreshTasks();
    } catch (caught) {
      setError(describeDashboardError(auth, caught, connectionState !== "offline"));
    } finally {
      setSubmittingCampaign(false);
    }
  };

  const handleReview = async (taskId: string, decision: "approve" | "reject") => {
    setReviewingTaskId(taskId);
    setError("");
    try {
      const result = decision === "approve" ? await approveTask(auth, taskId) : await rejectTask(auth, taskId);
      setNotice(`${result.outcome}: ${result.reason}`);
      await refreshTasks();
    } catch (caught) {
      setError(describeDashboardError(auth, caught, connectionState !== "offline"));
    } finally {
      setReviewingTaskId(null);
    }
  };

  const handleDeadLetterReplay = async (taskId: string) => {
    setReplayingTaskId(taskId);
    setError("");
    try {
      const replayed = await replayDeadLetterTask(auth, taskId);
      setNotice(`Replayed ${replayed.taskId} back to PENDING queue.`);
      await refreshTasks();
    } catch (caught) {
      setError(describeDashboardError(auth, caught, connectionState !== "offline"));
    } finally {
      setReplayingTaskId(null);
    }
  };

  const handleRestoreDefaults = () => {
    authRef.current = defaultAuth;
    setAuth(defaultAuth);
    setError("");
    setNotice("Restored local development defaults. Refreshing dashboard.");
    void refreshTasks();
  };

  const statusCounts = useMemo(() => buildStatusCounts(tasks), [tasks]);

  const reviewQueue = useMemo(() => tasks.filter(isActionableReviewTask), [tasks]);

  const governedReviewCount = useMemo(
    () => tasks.filter(isGovernedReviewTask).length,
    [tasks]
  );

  const workerSummaries = useMemo(() => buildWorkerSummaries(tasks), [tasks]);

  const displayTelemetry = useMemo(
    () => ({
      tenantId: telemetry?.tenantId ?? auth.tenantId,
      taskQueueDepth: telemetry?.taskQueueDepth ?? tasks.filter(isActiveTask).length,
      reviewQueueDepth: telemetry?.reviewQueueDepth ?? governedReviewCount,
      deadLetterQueueDepth: telemetry?.deadLetterQueueDepth ?? (statusCounts.REJECTED ?? 0),
      totalTasks: telemetry?.totalTasks ?? tasks.length,
      statusCounts: telemetry?.statusCounts ?? statusCounts,
      queueBackend: telemetry?.queueBackend ?? "client-derived",
      walletProvider: telemetry?.walletProvider ?? "not reported",
      dailyBudgetUsd: telemetry?.dailyBudgetUsd ?? 0,
      todaySpendUsd: telemetry?.todaySpendUsd ?? 0,
      remainingBudgetUsd:
        telemetry?.remainingBudgetUsd ??
        Math.max((telemetry?.dailyBudgetUsd ?? 0) - (telemetry?.todaySpendUsd ?? 0), 0),
      walletTransfersToday: telemetry?.walletTransfersToday ?? 0,
      spendDeltaVsYesterdayUsd: telemetry?.spendDeltaVsYesterdayUsd ?? 0,
      trendSignalsToday: telemetry?.trendSignalsToday ?? 0,
      topTrendTopicsToday: telemetry?.topTrendTopicsToday ?? [],
      workerP50LatencyMs: telemetry?.workerP50LatencyMs ?? 0,
      workerP95LatencyMs: telemetry?.workerP95LatencyMs ?? 0,
      successfulExecutionsToday: telemetry?.successfulExecutionsToday ?? (statusCounts.COMPLETE ?? 0),
      failedExecutionsToday: telemetry?.failedExecutionsToday ?? (statusCounts.REJECTED ?? 0),
      retryAttemptsToday: telemetry?.retryAttemptsToday ?? 0,
      deadLetteredTasksToday: telemetry?.deadLetteredTasksToday ?? (statusCounts.REJECTED ?? 0)
    }),
    [auth.tenantId, governedReviewCount, statusCounts, tasks, telemetry]
  );

  const completionRate = useMemo(() => {
    if (displayTelemetry.totalTasks === 0) {
      return 0;
    }
    return Math.round(
      (((displayTelemetry.statusCounts.COMPLETE ?? 0) as number) / displayTelemetry.totalTasks) * 100
    );
  }, [displayTelemetry.statusCounts.COMPLETE, displayTelemetry.totalTasks]);

  const updateAuth = (patch: Partial<AuthConfig>) => setAuth((current) => ({ ...current, ...patch }));

  const authMode = auth.bearerToken.trim() ? "Bearer token" : "API key";

  const connectionSummary =
    connectionState === "live"
      ? "Live backend"
      : connectionState === "degraded"
        ? "Partial data"
        : connectionState === "offline"
          ? "Offline"
          : "Checking";

  const connectionHint =
    connectionState === "live"
      ? `Healthy sync at ${formatSyncTime(lastSyncAt)}`
      : connectionState === "degraded"
        ? `API reachable with fallback counters. Last sync ${formatSyncTime(lastSyncAt)}`
        : connectionState === "offline"
          ? `No connection to ${auth.baseUrl}`
          : "Testing dashboard connectivity";

  return (
    <main className="app-shell">
      <div className="bg-orb orb-left" aria-hidden="true" />
      <div className="bg-orb orb-right" aria-hidden="true" />

      <header className="panel hero stagger-item">
        <p className="eyebrow">Project Chimera Console</p>
        <h1>Autonomous Influencer Mission Control</h1>
        <p className="hero-subtitle">
          Operate campaign orchestration, governance review, and tenant-scoped task flow from one surface.
        </p>
      </header>

      <section className="panel auth-panel stagger-item">
        <div className="section-header">
          <h2>Connection and Auth Context</h2>
          <p>Matches API contract headers (`X-Tenant-Id`, `X-Role`, API key or bearer token).</p>
        </div>

        <div className="auth-grid">
          <label>
            API Base URL
            <input value={auth.baseUrl} onChange={(event) => updateAuth({ baseUrl: event.target.value })} />
          </label>
          <label>
            Tenant ID
            <input value={auth.tenantId} onChange={(event) => updateAuth({ tenantId: event.target.value })} />
          </label>
          <label>
            API Key
            <input value={auth.apiKey} onChange={(event) => updateAuth({ apiKey: event.target.value })} />
          </label>
          <label>
            Bearer Token (optional)
            <input
              value={auth.bearerToken}
              onChange={(event) => updateAuth({ bearerToken: event.target.value })}
              placeholder="Bearer eyJ... or raw token"
            />
          </label>
        </div>

        <div className="toolbar">
          <div className="toolbar-actions">
            <button type="button" className="button button-secondary" onClick={() => void refreshTasks()} disabled={loadingTasks}>
              {loadingTasks ? "Refreshing..." : "Refresh Tasks"}
            </button>
            <button
              type="button"
              className="button button-tertiary"
              onClick={() => setAutoRefresh((current) => !current)}
            >
              {autoRefresh ? "Auto-Refresh On" : "Auto-Refresh Off"}
            </button>
            <button type="button" className="button button-tertiary" onClick={handleRestoreDefaults}>
              Restore Local Defaults
            </button>
          </div>

          <p className="toolbar-hint">
            Auth mode: {authMode}. Telemetry source: {telemetryMode === "live" ? "live API" : "client derived"}.
            Wallet mode: {displayTelemetry.walletProvider}. Queue backend: {displayTelemetry.queueBackend}.
          </p>
        </div>
      </section>

      <section className="panel ops-strip stagger-item" aria-label="dashboard runtime status">
        <p className={`status-chip status-${connectionState}`}>Connection: {connectionSummary}</p>
        <p className="status-chip status-neutral">Last sync: {formatSyncTime(lastSyncAt)}</p>
        <p className="status-chip status-neutral">Telemetry: {telemetryMode === "live" ? "Live" : "Fallback"}</p>
        <p className="status-chip status-neutral">Auto-refresh: {autoRefresh ? "15s" : "Off"}</p>
        <p className="ops-strip-hint">{connectionHint}</p>
      </section>

      <section className="metrics-grid stagger-item">
        <MetricCard label="Total Tasks" value={String(displayTelemetry.totalTasks)} hint="Tenant scoped" icon="01" />
        <MetricCard
          label="Review Queue"
          value={String(displayTelemetry.reviewQueueDepth)}
          hint="Governed escalations"
          icon="02"
        />
        <MetricCard
          label="Wallet Spend Today"
          value={formatUsd(displayTelemetry.todaySpendUsd)}
          hint={`${displayTelemetry.walletTransfersToday} transfer(s)`}
          icon="03"
        />
        <MetricCard
          label="Remaining Budget"
          value={formatUsd(displayTelemetry.remainingBudgetUsd)}
          hint={formatDelta(displayTelemetry.spendDeltaVsYesterdayUsd)}
          icon="04"
        />
        <MetricCard label="Completion Rate" value={`${completionRate}%`} hint="Auto + human approved" icon="05" />
        <MetricCard
          label="Runtime Queue Depth"
          value={`${displayTelemetry.taskQueueDepth} task_queue`}
          hint="Planner -> worker backlog"
          icon="06"
        />
        <MetricCard
          label="Trend Signals (24h)"
          value={String(displayTelemetry.trendSignalsToday)}
          hint={formatTrendTopics(displayTelemetry.topTrendTopicsToday)}
          icon="07"
        />
        <MetricCard
          label="Worker P95 Latency"
          value={`${displayTelemetry.workerP95LatencyMs} ms`}
          hint={`p50 ${displayTelemetry.workerP50LatencyMs} ms`}
          icon="08"
        />
        <MetricCard
          label="Execution Success Rate"
          value={formatExecutionRate(
            displayTelemetry.successfulExecutionsToday,
            displayTelemetry.failedExecutionsToday
          )}
          hint={`${displayTelemetry.successfulExecutionsToday} ok / ${displayTelemetry.failedExecutionsToday} failed`}
          icon="09"
        />
        <MetricCard
          label="Dead-Letter Queue"
          value={String(displayTelemetry.deadLetterQueueDepth)}
          hint={`${displayTelemetry.deadLetteredTasksToday} dead-lettered today`}
          icon="10"
        />
        <MetricCard
          label="Retry Attempts"
          value={String(displayTelemetry.retryAttemptsToday)}
          hint="Retries scheduled today"
          icon="11"
        />
      </section>

      <section className="panel fleet-panel stagger-item">
        <div className="section-header">
          <h2>Fleet Pulse</h2>
          <p>Worker-level readiness, governed review load, and the latest operational context for each agent.</p>
        </div>

        {workerSummaries.length === 0 ? (
          <p className="empty-state">No worker activity yet. Create a campaign to generate fleet telemetry.</p>
        ) : (
          <div className="fleet-grid">
            {workerSummaries.map((worker) => {
              const completeWidth = Math.round((worker.completedTasks / worker.totalTasks) * 100);
              const reviewWidth = Math.round((worker.reviewTasks / worker.totalTasks) * 100);
              const rejectedWidth = Math.round((worker.rejectedTasks / worker.totalTasks) * 100);

              return (
                <article key={worker.workerId} className="fleet-card">
                  <div className="fleet-card-header">
                    <div>
                      <p className="fleet-worker">{worker.workerId}</p>
                      <p className="fleet-goal">{truncate(worker.latestGoal, 96)}</p>
                    </div>
                    <span className={`status-pill status-${worker.latestStatus.toLowerCase()}`}>
                      {worker.latestStatus}
                    </span>
                  </div>

                  <div className="fleet-stats">
                    <p>
                      <strong>{worker.totalTasks}</strong> total
                    </p>
                    <p>
                      <strong>{worker.activeTasks}</strong> active
                    </p>
                    <p>
                      <strong>{worker.reviewTasks}</strong> governed
                    </p>
                    <p>
                      <strong>{worker.completedTasks}</strong> complete
                    </p>
                  </div>

                  <div className="fleet-meter" aria-hidden="true">
                    {completeWidth > 0 && (
                      <span
                        className="fleet-meter-segment fleet-meter-complete"
                        style={{ width: `${completeWidth}%` }}
                      />
                    )}
                    {reviewWidth > 0 && (
                      <span
                        className="fleet-meter-segment fleet-meter-review"
                        style={{ width: `${reviewWidth}%` }}
                      />
                    )}
                    {rejectedWidth > 0 && (
                      <span
                        className="fleet-meter-segment fleet-meter-rejected"
                        style={{ width: `${rejectedWidth}%` }}
                      />
                    )}
                  </div>

                  <p className="fleet-meta">Last activity {new Date(worker.latestCreatedAt).toLocaleString()}</p>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <div className="dashboard-grid">
        <CampaignComposer busy={submittingCampaign} onSubmit={handleCampaignSubmit} />

        <section className="panel review-panel">
          <div className="section-header">
            <h2>HITL Review Queue</h2>
            <p>Approve or reject sensitive and queued tasks through reviewer-governed actions.</p>
          </div>

          {reviewQueue.length === 0 ? (
            <p className="empty-state">No review tasks right now. Create a campaign or refresh data.</p>
          ) : (
            <div className="review-grid">
              {reviewQueue.map((task) => (
                <ReviewCard
                  key={task.taskId}
                  taskId={task.taskId}
                  generatedContent={task.context.goalDescription}
                  reasoningTrace={task.context.personaConstraints.join("\n")}
                  confidenceScore={confidenceFromStatus(task.status)}
                  status={task.status}
                  createdAt={task.createdAt}
                  onApprove={() => void handleReview(task.taskId, "approve")}
                  onReject={() => void handleReview(task.taskId, "reject")}
                  busy={reviewingTaskId === task.taskId}
                />
              ))}
            </div>
          )}
        </section>
      </div>

      <TaskTable
        tasks={tasks}
        replayingTaskId={replayingTaskId}
        onReplayDeadLetter={(taskId) => void handleDeadLetterReplay(taskId)}
      />

      {(error || notice) && (
        <section className="panel message-panel">
          {error && <p className="error-text">{error}</p>}
          {notice && <p className="notice-text">{notice}</p>}
        </section>
      )}
    </main>
  );
}

export default App;
