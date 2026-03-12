package org.chimera.tests.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.chimera.action.SocialPublishingService;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskResult;
import org.chimera.model.TaskType;
import org.chimera.worker.WorkerService;
import org.junit.jupiter.api.Test;

class WorkerServiceLoadTest {

  @Test
  void shouldExecuteConcurrentBatchWithinSwarmSlo() {
    int taskCount = Integer.getInteger("chimera.load.taskCount", 200);
    long simulatedWorkMs = Long.getLong("chimera.load.simulatedWorkMs", 40L);
    long absoluteMaxElapsedMs = Long.getLong("chimera.load.maxElapsedMs", 4_000L);

    McpToolClient delayedToolClient =
        (toolName, arguments) -> {
          sleep(simulatedWorkMs);
          return new McpToolResult(
              true, "ok", Map.of("external_id", toolName + "-" + UUID.randomUUID()));
        };

    List<Task> tasks =
        IntStream.range(0, taskCount)
            .mapToObj(
                index ->
                    Task.pending(
                        "tenant-load",
                        TaskType.GENERATE_CONTENT,
                        Priority.MEDIUM,
                        new TaskContext(
                            "Load validation content task " + index,
                            List.of("Respect disclosure policy"),
                            List.of("news://ethiopia/fashion/trends")),
                        "worker-load-" + index))
            .toList();

    try (WorkerService workerService =
        new WorkerService(new SocialPublishingService(delayedToolClient))) {
      List<CompletableFuture<TaskResult>> futures =
          tasks.stream().map(workerService::execute).toList();

      Instant startedAt = Instant.now();
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();

      List<TaskResult> results = futures.stream().map(CompletableFuture::join).toList();
      long sequentialEstimateMs = simulatedWorkMs * taskCount;
      long relativeThresholdMs = Math.max(1_000L, sequentialEstimateMs / 2);

      assertThat(results).hasSize(taskCount);
      assertThat(results).allMatch(TaskResult::success);
      assertThat(elapsedMs).isLessThan(relativeThresholdMs);
      assertThat(elapsedMs).isLessThan(absoluteMaxElapsedMs);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during simulated MCP workload", ex);
    }
  }
}
