package org.chimera.tests.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.chimera.action.SocialPublishingService;
import org.chimera.creative.CreativeEngineService;
import org.chimera.mcp.McpToolClient;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskType;
import org.chimera.wallet.SimulatedWalletProvider;
import org.chimera.wallet.WalletExecutionService;
import org.chimera.worker.WorkerService;
import org.junit.jupiter.api.Test;

class WorkerServiceTest {

  @Test
  void shouldInvokePublishToolForGenerateContentTask() {
    AtomicReference<String> capturedTool = new AtomicReference<>();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          capturedTool.set(toolName);
          return new McpToolResult(true, "ok", Map.of("external_id", "tweet-123"));
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(capturedTool.get()).isEqualTo("twitter.post_tweet");
    assertThat(result.success()).isTrue();
    assertThat(result.payload()).isEqualTo("tweet-123");
    assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.9);
    workerService.close();
  }

  @Test
  void shouldInvokeReplyToolForReplyTask() {
    AtomicReference<String> capturedTool = new AtomicReference<>();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          capturedTool.set(toolName);
          return new McpToolResult(true, "ok", Map.of("external_id", "reply-999"));
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.REPLY_COMMENT,
            Priority.MEDIUM,
            new TaskContext(
                "Engage with audience",
                List.of("Respect persona directives"),
                List.of("conversation://thread-77")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(capturedTool.get()).isEqualTo("twitter.reply_tweet");
    assertThat(result.success()).isTrue();
    assertThat(result.payload()).isEqualTo("reply-999");
    assertThat(result.confidenceScore()).isLessThan(0.9);
    workerService.close();
  }

  @Test
  void shouldExecuteTransactionTaskWhenWalletServiceConfigured() {
    WorkerService workerService =
        new WorkerService(null, new WalletExecutionService(new SimulatedWalletProvider()));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.EXECUTE_TRANSACTION,
            Priority.HIGH,
            new TaskContext(
                "Transfer creator budget",
                List.of("Respect persona directives"),
                List.of("wallet://to/0xabc123", "wallet://amount_usd/15.00")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isTrue();
    assertThat(result.payload()).startsWith("sim-tx-");
    assertThat(result.reasoningTrace()).contains("simulated");
    workerService.close();
  }

  @Test
  void shouldReturnHonestDisclosureForAiIdentityInquiry() {
    AtomicReference<Map<String, Object>> capturedArgs = new AtomicReference<>();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          capturedArgs.set(arguments);
          return new McpToolResult(true, "ok", Map.of("external_id", "reply-identity"));
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.REPLY_COMMENT,
            Priority.MEDIUM,
            new TaskContext(
                "User asks: are you AI or human?",
                List.of("Respect persona directives"),
                List.of("conversation://identity-thread")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isTrue();
    assertThat(capturedArgs.get())
        .containsEntry("text_content", "I am a virtual persona created by AI. #AIAssisted");
    workerService.close();
  }

  @Test
  void shouldUseCreativeEngineAssetsWhenConsistencyPasses() {
    AtomicReference<Map<String, Object>> publishArgs = new AtomicReference<>();
    AtomicInteger publishCalls = new AtomicInteger();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          return switch (toolName) {
            case "creative.generate_text" ->
                new McpToolResult(
                    true, "text-ok", Map.of("text_content", "Drop the eco capsule tonight."));
            case "creative.generate_image" ->
                new McpToolResult(
                    true, "image-ok", Map.of("image_url", "https://cdn.example.com/look.png"));
            case "creative.generate_video" ->
                new McpToolResult(
                    true, "video-ok", Map.of("video_url", "https://cdn.example.com/reel.mp4"));
            case "creative.check_consistency" ->
                new McpToolResult(
                    true,
                    "consistency-ok",
                    Map.of("is_consistent", true, "consistency_score", 0.91));
            case "twitter.post_tweet" -> {
              publishArgs.set(arguments);
              publishCalls.incrementAndGet();
              yield new McpToolResult(true, "ok", Map.of("external_id", "tweet-creative-1"));
            }
            default -> throw new IllegalArgumentException("Unexpected tool: " + toolName);
          };
        };

    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(toolClient), null, new CreativeEngineService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isTrue();
    assertThat(result.payload()).isEqualTo("tweet-creative-1");
    assertThat(result.reasoningTrace()).contains("creative_consistency_passed=true");
    assertThat(result.reasoningTrace()).contains("media_count=2");
    assertThat(publishCalls.get()).isEqualTo(1);
    assertThat(publishArgs.get()).containsEntry("text_content", "Drop the eco capsule tonight.");
    assertThat((List<String>) publishArgs.get().get("media_urls"))
        .containsExactly("https://cdn.example.com/look.png", "https://cdn.example.com/reel.mp4");
    workerService.close();
  }

  @Test
  void shouldBlockPublishWhenCreativeConsistencyLockFails() {
    AtomicInteger publishCalls = new AtomicInteger();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          return switch (toolName) {
            case "creative.generate_text" ->
                new McpToolResult(
                    true, "text-ok", Map.of("text_content", "Drop the eco capsule tonight."));
            case "creative.generate_image" ->
                new McpToolResult(
                    true, "image-ok", Map.of("image_url", "https://cdn.example.com/look.png"));
            case "creative.generate_video" ->
                new McpToolResult(
                    true, "video-ok", Map.of("video_url", "https://cdn.example.com/reel.mp4"));
            case "creative.check_consistency" ->
                new McpToolResult(
                    true,
                    "consistency-low",
                    Map.of("is_consistent", true, "consistency_score", 0.64));
            case "twitter.post_tweet" -> {
              publishCalls.incrementAndGet();
              yield new McpToolResult(true, "ok", Map.of("external_id", "tweet-creative-2"));
            }
            default -> throw new IllegalArgumentException("Unexpected tool: " + toolName);
          };
        };

    WorkerService workerService =
        new WorkerService(
            new SocialPublishingService(toolClient), null, new CreativeEngineService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Launch sustainable fashion campaign",
                List.of("Respect persona directives"),
                List.of("news://ethiopia/fashion/trends")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isFalse();
    assertThat(result.payload()).isEqualTo("creative-consistency-lock-failed");
    assertThat(result.reasoningTrace()).contains("creative_consistency_passed=false");
    assertThat(publishCalls.get()).isZero();
    workerService.close();
  }

  @Test
  void shouldRouteGenerateContentToInstagramWhenResourceIndicatesInstagram() {
    AtomicReference<String> capturedTool = new AtomicReference<>();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          capturedTool.set(toolName);
          return new McpToolResult(true, "ok", Map.of("external_id", "ig-123"));
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Publish launch teaser",
                List.of("Respect persona directives"),
                List.of("instagram://brand/account")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isTrue();
    assertThat(capturedTool.get()).isEqualTo("instagram.publish_media");
    workerService.close();
  }

  @Test
  void shouldRetrySocialPublishOnTransientFailure() {
    AtomicInteger attemptCounter = new AtomicInteger();
    McpToolClient toolClient =
        (toolName, arguments) -> {
          if (!"twitter.post_tweet".equals(toolName)) {
            return new McpToolResult(true, "ok", Map.of("external_id", "other-tool"));
          }
          int attempt = attemptCounter.incrementAndGet();
          if (attempt < 3) {
            throw new IllegalStateException("transient publish failure");
          }
          return new McpToolResult(true, "ok", Map.of("external_id", "tweet-789"));
        };
    WorkerService workerService = new WorkerService(new SocialPublishingService(toolClient));

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext(
                "Publish launch teaser",
                List.of("Respect persona directives"),
                List.of("twitter://mentions/recent")),
            "worker-alpha");

    var result = workerService.execute(task).join();

    assertThat(result.success()).isTrue();
    assertThat(result.payload()).isEqualTo("tweet-789");
    assertThat(attemptCounter.get()).isEqualTo(3);
    workerService.close();
  }
}
