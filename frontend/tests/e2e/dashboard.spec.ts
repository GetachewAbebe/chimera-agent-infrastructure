import { expect, test } from "@playwright/test";

type MockTaskStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "REVIEW"
  | "COMPLETE"
  | "REJECTED"
  | "ESCALATED";

type MockTask = {
  taskId: string;
  tenantId: string;
  taskType: string;
  priority: string;
  context: {
    goalDescription: string;
    personaConstraints: string[];
    requiredResources: string[];
  };
  assignedWorkerId: string;
  createdAt: string;
  status: MockTaskStatus;
};

const telemetryFor = (tasks: MockTask[]) => {
  const statusCounts: Record<string, number> = {};
  for (const task of tasks) {
    statusCounts[task.status] = (statusCounts[task.status] ?? 0) + 1;
  }
  return {
    tenantId: "tenant-alpha",
    taskQueueDepth: tasks.filter((task) => task.status === "PENDING" || task.status === "IN_PROGRESS").length,
    reviewQueueDepth: tasks.filter((task) => task.status === "REVIEW" || task.status === "ESCALATED").length,
    deadLetterQueueDepth: tasks.filter((task) => task.status === "REJECTED").length,
    totalTasks: tasks.length,
    statusCounts,
    queueBackend: "in-memory",
    walletProvider: "simulated",
    dailyBudgetUsd: 500,
    todaySpendUsd: 0,
    remainingBudgetUsd: 500,
    walletTransfersToday: 0,
    spendDeltaVsYesterdayUsd: 0,
    trendSignalsToday: 0,
    topTrendTopicsToday: [],
    workerP50LatencyMs: 0,
    workerP95LatencyMs: 0,
    successfulExecutionsToday: tasks.filter((task) => task.status === "COMPLETE").length,
    failedExecutionsToday: tasks.filter((task) => task.status === "REJECTED").length,
    retryAttemptsToday: 0,
    deadLetteredTasksToday: tasks.filter((task) => task.status === "REJECTED").length
  };
};

const createTask = (taskId: string, status: MockTaskStatus, goal: string): MockTask => ({
  taskId,
  tenantId: "tenant-alpha",
  taskType: "GENERATE_CONTENT",
  priority: "HIGH",
  context: {
    goalDescription: goal,
    personaConstraints: ["Respect disclosure policy"],
    requiredResources: ["news://ethiopia/fashion/trends"]
  },
  assignedWorkerId: "worker-alpha",
  createdAt: new Date().toISOString(),
  status
});

test("should persist auth context and prefer bearer token over API key", async ({ page }) => {
  const taskHeaders: Array<Record<string, string>> = [];
  const tasks: MockTask[] = [];

  await page.route("http://localhost:8080/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === "/api/tasks" && request.method() === "GET") {
      taskHeaders.push(request.headers());
      await route.fulfill({ status: 200, json: tasks });
      return;
    }

    if (path === "/api/telemetry" && request.method() === "GET") {
      await route.fulfill({ status: 200, json: telemetryFor(tasks) });
      return;
    }

    await route.fulfill({
      status: 404,
      json: { error: "not_found", detail: `Unmocked path: ${path}` }
    });
  });

  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Autonomous Influencer Mission Control" })).toBeVisible();
  await expect(page.locator(".notice-text")).toContainText("Loaded 0 task(s).");
  await expect(page.getByLabel("API Key")).toHaveValue("dev-tenant-alpha-key");

  await page.getByLabel("Bearer Token (optional)").fill("eyJ-test-token");
  await page.getByLabel("Tenant ID").fill("tenant-alpha-e2e");
  await page.getByRole("button", { name: "Refresh Tasks" }).click();

  await expect.poll(() => taskHeaders.length).toBeGreaterThan(1);
  const latestHeaders = taskHeaders.at(-1) ?? {};
  expect(latestHeaders.authorization).toContain("Bearer eyJ-test-token");
  expect(latestHeaders["x-api-key"] ?? "").toBe("");
  expect(latestHeaders["x-tenant-id"]).toBe("tenant-alpha-e2e");

  await page.reload();
  await expect(page.getByLabel("Bearer Token (optional)")).toHaveValue("eyJ-test-token");
  await expect(page.getByLabel("Tenant ID")).toHaveValue("tenant-alpha-e2e");
});

test("should run campaign creation, review reject, replay, and cooldown error flow", async ({ page }) => {
  let taskCounter = 0;
  let replayCount = 0;
  const tasks: MockTask[] = [];

  await page.route("http://localhost:8080/api/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;

    if (path === "/api/tasks" && request.method() === "GET") {
      await route.fulfill({ status: 200, json: tasks });
      return;
    }

    if (path === "/api/telemetry" && request.method() === "GET") {
      await route.fulfill({ status: 200, json: telemetryFor(tasks) });
      return;
    }

    if (path === "/api/campaigns" && request.method() === "POST") {
      taskCounter += 1;
      const taskId = `00000000-0000-4000-8000-${String(taskCounter).padStart(12, "0")}`;
      const createdTask = createTask(taskId, "PENDING", "E2E UI campaign");
      tasks.splice(0, tasks.length, createdTask);
      await route.fulfill({ status: 201, json: [createdTask] });
      return;
    }

    const rejectMatch = path.match(/^\/api\/review\/([^/]+)\/reject$/);
    if (rejectMatch && request.method() === "POST") {
      const taskId = rejectMatch[1];
      const target = tasks.find((task) => task.taskId === taskId);
      if (!target) {
        await route.fulfill({
          status: 404,
          json: { error: "invalid_request", detail: "task not found" }
        });
        return;
      }
      target.status = "REJECTED";
      await route.fulfill({
        status: 200,
        json: {
          outcome: "REJECTED",
          nextStatus: "REJECTED",
          reason: `Human reviewer rejected task ${taskId}`
        }
      });
      return;
    }

    const replayMatch = path.match(/^\/api\/dead-letter\/([^/]+)\/replay$/);
    if (replayMatch && request.method() === "POST") {
      const taskId = replayMatch[1];
      const target = tasks.find((task) => task.taskId === taskId);
      if (!target) {
        await route.fulfill({
          status: 404,
          json: { error: "invalid_request", detail: "task not found" }
        });
        return;
      }
      if (target.status !== "REJECTED") {
        await route.fulfill({
          status: 409,
          json: { error: "invalid_state", detail: "Only REJECTED tasks can be replayed" }
        });
        return;
      }
      replayCount += 1;
      if (replayCount > 1) {
        await route.fulfill({
          status: 429,
          json: {
            error: "replay_cooldown_active",
            detail: `Replay cooldown active for task ${taskId}, retry in 300s`
          }
        });
        return;
      }
      target.status = "PENDING";
      await route.fulfill({ status: 200, json: target });
      return;
    }

    await route.fulfill({
      status: 404,
      json: { error: "not_found", detail: `Unmocked path: ${path}` }
    });
  });

  await page.goto("/");
  await page.getByLabel("Goal").fill("Drive spring campaign momentum");
  await page.getByRole("button", { name: "Create Campaign" }).click();

  await expect(page.locator(".notice-text")).toContainText(
    /(Created 1 task\(s\) from campaign goal\.|Loaded 1 task\(s\)\.)/
  );
  await expect(page.locator(".review-card")).toHaveCount(1);
  await expect(page.locator("table tbody tr")).toHaveCount(1);

  await page.locator(".review-card").first().getByRole("button", { name: "Reject" }).click();
  await expect(page.locator("table tbody tr")).toContainText("REJECTED");
  await expect(page.getByRole("button", { name: "Replay" })).toBeVisible();

  await page.getByRole("button", { name: "Replay" }).click();
  await expect(page.locator("table tbody tr")).toContainText("PENDING");

  await page.locator(".review-card").first().getByRole("button", { name: "Reject" }).click();
  await expect(page.getByRole("button", { name: "Replay" })).toBeVisible();

  await page.getByRole("button", { name: "Replay" }).click();
  await expect(page.locator(".error-text")).toContainText("Replay cooldown active");
  await expect(page.locator(".error-text")).toContainText("HTTP 429");
  await expect(page.getByText("Dead-Letter Queue")).toBeVisible();
});

test("should show actionable guidance when the backend is unreachable", async ({ page }) => {
  await page.route("http://localhost:8080/api/**", async (route) => {
    await route.abort();
  });
  await page.route("http://localhost:8080/health", async (route) => {
    await route.abort();
  });

  await page.goto("/");

  await expect(page.locator(".error-text")).toContainText("Unable to reach http://localhost:8080");
  await expect(page.locator(".error-text")).toContainText("PORT=8080 make run");
});
