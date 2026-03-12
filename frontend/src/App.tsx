import { useEffect, useMemo, useState } from "react";
import {
  ApiRequestError,
  approveTask,
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

const defaultAuth: AuthConfig = {
  baseUrl: "http://localhost:8080",
  tenantId: "tenant-alpha",
  apiKey: "dev-tenant-alpha-key",
  bearerToken: ""
};

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

const statusOrder = ["PENDING", "IN_PROGRESS", "REVIEW", "ESCALATED", "COMPLETE", "REJECTED"];

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

const errorMessage = (error: unknown): string => {
  if (error instanceof ApiRequestError) {
    return `${error.message} (HTTP ${error.status})`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unexpected error";
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

function App() {
  const [auth, setAuth] = useState<AuthConfig>(loadAuth);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [telemetry, setTelemetry] = useState<TelemetrySnapshot | null>(null);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [submittingCampaign, setSubmittingCampaign] = useState(false);
  const [reviewingTaskId, setReviewingTaskId] = useState<string | null>(null);
  const [replayingTaskId, setReplayingTaskId] = useState<string | null>(null);
  const [error, setError] = useState<string>("");
  const [notice, setNotice] = useState<string>("");

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  }, [auth]);

  const refreshTasks = async () => {
    setLoadingTasks(true);
    setError("");
    try {
      const latest = await listTasks(auth);
      try {
        const telemetrySnapshot = await getTelemetry(auth);
        setTelemetry(telemetrySnapshot);
      } catch {
        setTelemetry(null);
      }
      setTasks(latest);
      setNotice(`Loaded ${latest.length} task(s).`);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoadingTasks(false);
    }
  };

  useEffect(() => {
    void refreshTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
      setError(errorMessage(caught));
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
      setError(errorMessage(caught));
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
      setError(errorMessage(caught));
    } finally {
      setReplayingTaskId(null);
    }
  };

  const statusCounts = useMemo(() => {
    const initial: Record<string, number> = {};
    for (const status of statusOrder) {
      initial[status] = 0;
    }
    for (const task of tasks) {
      initial[task.status] = (initial[task.status] ?? 0) + 1;
    }
    return initial;
  }, [tasks]);

  const reviewQueue = useMemo(
    () => tasks.filter((task) => task.status === "PENDING" || task.status === "REVIEW" || task.status === "ESCALATED"),
    [tasks]
  );

  const completionRate = useMemo(() => {
    if (tasks.length === 0) {
      return 0;
    }
    return Math.round(((statusCounts.COMPLETE ?? 0) / tasks.length) * 100);
  }, [tasks.length, statusCounts.COMPLETE]);

  const updateAuth = (patch: Partial<AuthConfig>) => setAuth((current) => ({ ...current, ...patch }));

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
          <button type="button" className="button button-secondary" onClick={() => void refreshTasks()} disabled={loadingTasks}>
            {loadingTasks ? "Refreshing..." : "Refresh Tasks"}
          </button>
          <p className="toolbar-hint">
            Viewer role is used for task listing and telemetry. Wallet mode: {telemetry?.walletProvider ?? "unknown"}.
            Queue backend: {telemetry?.queueBackend ?? "in-memory"}.
          </p>
        </div>
      </section>

      <section className="metrics-grid stagger-item">
        <MetricCard label="Total Tasks" value={String(telemetry?.totalTasks ?? tasks.length)} hint="Tenant scoped" icon="01" />
        <MetricCard
          label="Review Queue"
          value={String(telemetry?.reviewQueueDepth ?? reviewQueue.length)}
          hint="Queued escalations"
          icon="02"
        />
        <MetricCard
          label="Wallet Spend Today"
          value={formatUsd(telemetry?.todaySpendUsd ?? 0)}
          hint={`${telemetry?.walletTransfersToday ?? 0} transfer(s)`}
          icon="03"
        />
        <MetricCard
          label="Remaining Budget"
          value={formatUsd(telemetry?.remainingBudgetUsd ?? 0)}
          hint={formatDelta(telemetry?.spendDeltaVsYesterdayUsd ?? 0)}
          icon="04"
        />
        <MetricCard label="Completion Rate" value={`${completionRate}%`} hint="Auto + human approved" icon="05" />
        <MetricCard
          label="Runtime Queue Depth"
          value={`${telemetry?.taskQueueDepth ?? 0} task_queue`}
          hint="Planner -> worker backlog"
          icon="06"
        />
        <MetricCard
          label="Trend Signals (24h)"
          value={String(telemetry?.trendSignalsToday ?? 0)}
          hint={formatTrendTopics(telemetry?.topTrendTopicsToday)}
          icon="07"
        />
        <MetricCard
          label="Worker P95 Latency"
          value={`${telemetry?.workerP95LatencyMs ?? 0} ms`}
          hint={`p50 ${telemetry?.workerP50LatencyMs ?? 0} ms`}
          icon="08"
        />
        <MetricCard
          label="Execution Success Rate"
          value={formatExecutionRate(telemetry?.successfulExecutionsToday, telemetry?.failedExecutionsToday)}
          hint={`${telemetry?.successfulExecutionsToday ?? 0} ok / ${telemetry?.failedExecutionsToday ?? 0} failed`}
          icon="09"
        />
        <MetricCard
          label="Dead-Letter Queue"
          value={String(telemetry?.deadLetterQueueDepth ?? 0)}
          hint={`${telemetry?.deadLetteredTasksToday ?? 0} dead-lettered today`}
          icon="10"
        />
        <MetricCard
          label="Retry Attempts"
          value={String(telemetry?.retryAttemptsToday ?? 0)}
          hint="Retries scheduled today"
          icon="11"
        />
      </section>

      <div className="dashboard-grid">
        <CampaignComposer busy={submittingCampaign} onSubmit={handleCampaignSubmit} />

        <section className="panel review-panel">
          <div className="section-header">
            <h2>HITL Review Queue</h2>
            <p>Approve or reject sensitive/queued tasks through reviewer-governed actions.</p>
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
