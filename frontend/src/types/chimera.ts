export type UserRole = "operator" | "reviewer" | "viewer";

export type TaskStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "REVIEW"
  | "COMPLETE"
  | "REJECTED"
  | "ESCALATED";

export interface TaskContext {
  goalDescription: string;
  personaConstraints: string[];
  requiredResources: string[];
}

export interface Task {
  taskId: string;
  tenantId: string;
  taskType: string;
  priority: string;
  context: TaskContext;
  assignedWorkerId: string;
  createdAt: string;
  status: TaskStatus;
}

export interface ReviewDecision {
  outcome: string;
  nextStatus: TaskStatus;
  reason: string;
}

export interface CreateCampaignRequest {
  goal: string;
  workerId?: string;
  requiredResources?: string[];
}

export interface TelemetrySnapshot {
  tenantId: string;
  taskQueueDepth: number;
  reviewQueueDepth: number;
  deadLetterQueueDepth: number;
  totalTasks: number;
  statusCounts: Record<string, number>;
  queueBackend: string;
  walletProvider: string;
  dailyBudgetUsd: number;
  todaySpendUsd: number;
  remainingBudgetUsd: number;
  walletTransfersToday: number;
  spendDeltaVsYesterdayUsd: number;
  trendSignalsToday: number;
  topTrendTopicsToday: string[];
  workerP50LatencyMs: number;
  workerP95LatencyMs: number;
  successfulExecutionsToday: number;
  failedExecutionsToday: number;
  retryAttemptsToday: number;
  deadLetteredTasksToday: number;
}

export interface ApiErrorResponse {
  code?: string;
  message?: string;
  error?: string;
  detail?: string;
}

export interface AuthConfig {
  baseUrl: string;
  tenantId: string;
  apiKey: string;
  bearerToken: string;
}
