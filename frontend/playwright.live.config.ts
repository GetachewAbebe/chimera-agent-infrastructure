import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  testMatch: /.*\.live\.spec\.ts$/,
  fullyParallel: false,
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: "http://127.0.0.1:4173",
    trace: "retain-on-failure"
  },
  webServer: [
    {
      command:
        "bash -lc 'cd .. && CHIMERA_WRITE_RATE_LIMIT_MAX_REQUESTS=120 CHIMERA_WRITE_RATE_LIMIT_WINDOW_SECONDS=60 CHIMERA_REPLAY_COOLDOWN_SECONDS=300 PORT=8180 mvn -q -Dmaven.repo.local=/tmp/.m2b -DskipTests exec:java -Dexec.mainClass=org.chimera.app.ChimeraApplication'",
      url: "http://127.0.0.1:8180/health",
      reuseExistingServer: !process.env.CI,
      timeout: 180_000
    },
    {
      command: "npm run dev -- --host 127.0.0.1 --port 4173",
      url: "http://127.0.0.1:4173",
      reuseExistingServer: !process.env.CI,
      timeout: 120_000
    }
  ]
});
