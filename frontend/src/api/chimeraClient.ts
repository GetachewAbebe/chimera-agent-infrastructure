import {
  type ApiErrorResponse,
  type AuthConfig,
  type CreateCampaignRequest,
  type ReviewDecision,
  type Task,
  type TelemetrySnapshot,
  type UserRole
} from "../types/chimera";

const trimSlash = (value: string): string => value.replace(/\/+$/, "");

const normalizeBearerToken = (token: string): string => {
  const trimmed = token.trim();
  if (trimmed.length === 0) {
    return "";
  }
  return trimmed.startsWith("Bearer ") ? trimmed : `Bearer ${trimmed}`;
};

const requestId = (): string => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `req-${Date.now()}`;
};

const buildHeaders = (auth: AuthConfig, role: UserRole): HeadersInit => {
  const tenantId = auth.tenantId.trim();
  if (!tenantId) {
    throw new Error("X-Tenant-Id is required.");
  }

  const token = normalizeBearerToken(auth.bearerToken);
  const apiKey = auth.apiKey.trim();
  if (!token && !apiKey) {
    throw new Error("Provide either an API key or a bearer token.");
  }

  const headers: Record<string, string> = {
    "X-Tenant-Id": tenantId,
    "X-Role": role,
    "X-Request-Id": requestId()
  };

  if (token) {
    headers.Authorization = token;
  } else {
    headers["X-Api-Key"] = apiKey;
  }

  return headers;
};

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const readError = async (response: Response): Promise<ApiRequestError> => {
  let payload: ApiErrorResponse | null = null;
  try {
    payload = (await response.json()) as ApiErrorResponse;
  } catch {
    payload = null;
  }

  const message = payload?.message ?? payload?.detail;
  const code = payload?.code ?? payload?.error;
  if (message) {
    return new ApiRequestError(response.status, message, code);
  }
  return new ApiRequestError(response.status, `Request failed with status ${response.status}`);
};

const apiUrl = (baseUrl: string, path: string): string => `${trimSlash(baseUrl)}${path}`;

export const checkApiHealth = async (baseUrl: string): Promise<boolean> => {
  const response = await fetch(apiUrl(baseUrl, "/health"), {
    method: "GET"
  });
  return response.ok;
};

export const listTasks = async (auth: AuthConfig): Promise<Task[]> => {
  const response = await fetch(apiUrl(auth.baseUrl, "/api/tasks"), {
    method: "GET",
    headers: buildHeaders(auth, "viewer")
  });

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as Task[];
};

export const createCampaign = async (
  auth: AuthConfig,
  payload: CreateCampaignRequest
): Promise<Task[]> => {
  const response = await fetch(apiUrl(auth.baseUrl, "/api/campaigns"), {
    method: "POST",
    headers: {
      ...buildHeaders(auth, "operator"),
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as Task[];
};

export const getTelemetry = async (auth: AuthConfig): Promise<TelemetrySnapshot> => {
  const response = await fetch(apiUrl(auth.baseUrl, "/api/telemetry"), {
    method: "GET",
    headers: buildHeaders(auth, "viewer")
  });

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as TelemetrySnapshot;
};

const review = async (
  auth: AuthConfig,
  taskId: string,
  decision: "approve" | "reject"
): Promise<ReviewDecision> => {
  const response = await fetch(apiUrl(auth.baseUrl, `/api/review/${taskId}/${decision}`), {
    method: "POST",
    headers: buildHeaders(auth, "reviewer")
  });

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as ReviewDecision;
};

export const approveTask = (auth: AuthConfig, taskId: string): Promise<ReviewDecision> =>
  review(auth, taskId, "approve");

export const rejectTask = (auth: AuthConfig, taskId: string): Promise<ReviewDecision> =>
  review(auth, taskId, "reject");

export const replayDeadLetterTask = async (auth: AuthConfig, taskId: string): Promise<Task> => {
  const response = await fetch(apiUrl(auth.baseUrl, `/api/dead-letter/${taskId}/replay`), {
    method: "POST",
    headers: buildHeaders(auth, "operator")
  });

  if (!response.ok) {
    throw await readError(response);
  }

  return (await response.json()) as Task;
};
