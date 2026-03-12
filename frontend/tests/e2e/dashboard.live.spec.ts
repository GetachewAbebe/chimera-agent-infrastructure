import { expect, test } from "@playwright/test";

const LIVE_TENANT = "tenant-alpha";
const LIVE_API_KEY = "dev-tenant-alpha-key";
const LIVE_API_BASE_URL = "http://127.0.0.1:8180";

test("should execute campaign to review to replay flow against live backend", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Autonomous Influencer Mission Control" })).toBeVisible();

  await page.getByLabel("API Base URL").fill(LIVE_API_BASE_URL);
  await page.getByLabel("Tenant ID").fill(LIVE_TENANT);
  await page.getByLabel("API Key").fill(LIVE_API_KEY);
  await page.getByLabel("Bearer Token (optional)").fill("");

  await page.getByRole("button", { name: "Refresh Tasks" }).click();
  await expect(page.locator(".notice-text")).toContainText(/Loaded \d+ task\(s\)\./);

  const sensitiveGoal = `Provide finance commentary for sneaker trend ${Date.now()}`;
  await page.getByLabel("Goal").fill(sensitiveGoal);
  await page.getByLabel("Worker ID").fill("worker-live-e2e");
  await page.getByRole("button", { name: "Create Campaign" }).click();

  await expect(page.locator(".notice-text")).toContainText(
    /(Created \d+ task\(s\) from campaign goal\.|Loaded \d+ task\(s\)\.)/
  );
  await expect(page.locator(".review-card")).toHaveCount(1);
  await expect(page.locator("table tbody tr")).toHaveCount(1);
  await expect(page.locator("table tbody tr")).toContainText("ESCALATED");

  await page.locator(".review-card").first().getByRole("button", { name: "Reject" }).click();
  await expect(page.locator("table tbody tr")).toContainText("REJECTED");
  await expect(page.getByRole("button", { name: "Replay" })).toBeVisible();

  await page.getByRole("button", { name: "Replay" }).click();
  await expect(page.locator("table tbody tr")).toContainText("PENDING");
});
