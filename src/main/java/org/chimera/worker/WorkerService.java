package org.chimera.worker;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.chimera.action.DisclosureLevel;
import org.chimera.action.SocialActionReceipt;
import org.chimera.action.SocialPlatform;
import org.chimera.action.SocialPostRequest;
import org.chimera.action.SocialPublishingService;
import org.chimera.action.SocialReplyRequest;
import org.chimera.creative.CreativeEngineService;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskResult;
import org.chimera.model.TaskType;
import org.chimera.wallet.WalletExecutionService;

public final class WorkerService implements AutoCloseable {
  private static final int SOCIAL_ACTION_MAX_ATTEMPTS = 3;
  private static final long SOCIAL_ACTION_BACKOFF_BASE_MS = 75L;

  private final ExecutorService workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final SocialPublishingService socialPublishingService;
  private final WalletExecutionService walletExecutionService;
  private final CreativeEngineService creativeEngineService;

  public WorkerService() {
    this(null, null, null);
  }

  public WorkerService(SocialPublishingService socialPublishingService) {
    this(socialPublishingService, null, null);
  }

  public WorkerService(
      SocialPublishingService socialPublishingService,
      WalletExecutionService walletExecutionService) {
    this(socialPublishingService, walletExecutionService, null);
  }

  public WorkerService(
      SocialPublishingService socialPublishingService,
      WalletExecutionService walletExecutionService,
      CreativeEngineService creativeEngineService) {
    this.socialPublishingService = socialPublishingService;
    this.walletExecutionService = walletExecutionService;
    this.creativeEngineService = creativeEngineService;
  }

  public CompletableFuture<TaskResult> execute(Task task) {
    return CompletableFuture.supplyAsync(() -> executeInternal(task), workerExecutor);
  }

  private TaskResult executeInternal(Task task) {
    if (task == null) {
      throw new IllegalArgumentException("task must not be null");
    }
    if (task.taskType() == TaskType.EXECUTE_TRANSACTION) {
      return executeTransactionPlaceholder(task);
    }
    if (socialPublishingService == null) {
      return new TaskResult(
          task.taskId(),
          task.assignedWorkerId(),
          true,
          0.91,
          "PENDING_IMPLEMENTATION",
          "Worker executed placeholder logic. Configure SocialPublishingService to enable MCP tools.",
          Instant.now());
    }

    return switch (task.taskType()) {
      case GENERATE_CONTENT -> executeGenerateContent(task);
      case REPLY_COMMENT -> executeReply(task);
      case EXECUTE_TRANSACTION -> executeTransactionPlaceholder(task);
    };
  }

  private TaskResult executeGenerateContent(Task task) {
    SocialPlatform platform = resolvePlatform(task.context());
    String text = buildPostText(task.context());
    List<String> mediaUrls = List.of();
    double consistencyScore = 0.93;
    String creativeTrace = "creative_consistency_passed=unknown; creative_consistency_score=0.93";

    if (creativeEngineService != null) {
      var composition = creativeEngineService.compose(task.assignedWorkerId(), task.context());
      text = firstNonBlank(composition.textContent(), text);
      mediaUrls = composition.mediaUrls();
      consistencyScore = composition.consistencyScore();
      creativeTrace = composition.trace();

      if (!composition.consistencyPassed()) {
        return new TaskResult(
            task.taskId(),
            task.assignedWorkerId(),
            false,
            Math.min(0.69, Math.max(0.40, consistencyScore)),
            "creative-consistency-lock-failed",
            "Creative consistency lock failed prior to publish. " + creativeTrace,
            Instant.now());
      }
    }

    String publishText = text;
    List<String> publishMediaUrls = mediaUrls;
    var receipt =
        executeSocialActionWithRetry(
            () ->
                socialPublishingService.publishPost(
                    new SocialPostRequest(
                        platform, publishText, publishMediaUrls, DisclosureLevel.AUTOMATED)));
    double confidence = receipt.success() ? Math.max(0.90, consistencyScore) : 0.52;
    return new TaskResult(
        task.taskId(),
        task.assignedWorkerId(),
        receipt.success(),
        confidence,
        receipt.externalId().isBlank() ? "no-external-id" : receipt.externalId(),
        "Executed "
            + receipt.toolName()
            + " for publish workflow. "
            + creativeTrace
            + "; media_count="
            + mediaUrls.size()
            + "; platform="
            + platform.name().toLowerCase(),
        Instant.now());
  }

  private TaskResult executeReply(Task task) {
    SocialPlatform platform = resolvePlatform(task.context());
    String conversationId = inferConversationId(task.context(), task.taskId().toString());
    String text = buildReplyText(task.context());
    var receipt =
        executeSocialActionWithRetry(
            () ->
                socialPublishingService.replyToInteraction(
                    new SocialReplyRequest(
                        platform, conversationId, text, DisclosureLevel.AUTOMATED)));

    return new TaskResult(
        task.taskId(),
        task.assignedWorkerId(),
        receipt.success(),
        receipt.success() ? 0.89 : 0.51,
        receipt.externalId().isBlank() ? conversationId : receipt.externalId(),
        "Executed "
            + receipt.toolName()
            + " for reply workflow. platform="
            + platform.name().toLowerCase(),
        Instant.now());
  }

  private TaskResult executeTransactionPlaceholder(Task task) {
    if (walletExecutionService != null) {
      try {
        var transferResult =
            walletExecutionService.executeTransaction(task.assignedWorkerId(), task.context());
        return new TaskResult(
            task.taskId(),
            task.assignedWorkerId(),
            transferResult.success(),
            transferResult.success() ? 0.92 : 0.48,
            transferResult.transactionId(),
            transferResult.provider()
                + " "
                + transferResult.status()
                + ": "
                + transferResult.message(),
            Instant.now());
      } catch (Exception ex) {
        return new TaskResult(
            task.taskId(),
            task.assignedWorkerId(),
            false,
            0.45,
            "transaction-failed",
            ex.getMessage(),
            Instant.now());
      }
    }

    return new TaskResult(
        task.taskId(),
        task.assignedWorkerId(),
        false,
        0.4,
        "transaction-provider-unavailable",
        "EXECUTE_TRANSACTION is blocked until wallet provider integration is configured.",
        Instant.now());
  }

  private static String buildPostText(TaskContext context) {
    String goal = context.goalDescription() == null ? "" : context.goalDescription().trim();
    if (goal.length() > 240) {
      goal = goal.substring(0, 240);
    }
    return goal + " #AIAssisted";
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary.trim();
    }
    return fallback;
  }

  private static String buildReplyText(TaskContext context) {
    if (isAiIdentityInquiry(context)) {
      return "I am a virtual persona created by AI. #AIAssisted";
    }

    String goal = context.goalDescription() == null ? "" : context.goalDescription().trim();
    if (goal.length() > 180) {
      goal = goal.substring(0, 180);
    }
    return "Thanks for engaging. " + goal + " #AIAssisted";
  }

  private static boolean isAiIdentityInquiry(TaskContext context) {
    if (context == null || context.goalDescription() == null) {
      return false;
    }
    String text = context.goalDescription().toLowerCase();
    return text.contains("are you ai")
        || text.contains("are you a robot")
        || text.contains("is this ai")
        || text.contains("human or ai")
        || text.contains("are you bot")
        || text.contains("are you an ai");
  }

  private static String inferConversationId(TaskContext context, String fallbackId) {
    List<String> resources =
        context.requiredResources() == null ? List.of() : context.requiredResources();
    for (String resource : resources) {
      if (resource == null) {
        continue;
      }
      String trimmed = resource.trim();
      if (trimmed.startsWith("conversation://")) {
        return trimmed.substring("conversation://".length());
      }
      if (trimmed.startsWith("twitter://")) {
        return trimmed.replace("twitter://", "");
      }
      if (trimmed.contains("://")) {
        return trimmed.substring(trimmed.indexOf("://") + "://".length());
      }
    }
    return fallbackId;
  }

  private SocialActionReceipt executeSocialActionWithRetry(
      Supplier<SocialActionReceipt> actionSupplier) {
    RuntimeException latestException = null;
    SocialActionReceipt latestReceipt = null;
    for (int attempt = 1; attempt <= SOCIAL_ACTION_MAX_ATTEMPTS; attempt++) {
      try {
        latestReceipt = actionSupplier.get();
        if (latestReceipt.success() || attempt == SOCIAL_ACTION_MAX_ATTEMPTS) {
          return latestReceipt;
        }
      } catch (RuntimeException ex) {
        latestException = ex;
        if (attempt == SOCIAL_ACTION_MAX_ATTEMPTS) {
          throw ex;
        }
      }

      sleepBackoff(attempt);
    }

    if (latestException != null) {
      throw latestException;
    }
    if (latestReceipt != null) {
      return latestReceipt;
    }
    throw new IllegalStateException("Social action retry loop exited without a result");
  }

  private static void sleepBackoff(int attempt) {
    try {
      Thread.sleep(SOCIAL_ACTION_BACKOFF_BASE_MS * attempt);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Retry backoff interrupted", ex);
    }
  }

  private static SocialPlatform resolvePlatform(TaskContext context) {
    List<String> resources =
        context == null || context.requiredResources() == null
            ? List.of()
            : context.requiredResources();

    for (String resource : resources) {
      if (resource == null || resource.isBlank()) {
        continue;
      }
      String trimmed = resource.trim().toLowerCase();
      if (trimmed.startsWith("instagram://")) {
        return SocialPlatform.INSTAGRAM;
      }
      if (trimmed.startsWith("threads://")) {
        return SocialPlatform.THREADS;
      }
      if (trimmed.startsWith("twitter://") || trimmed.startsWith("conversation://")) {
        return SocialPlatform.TWITTER;
      }
    }
    return SocialPlatform.TWITTER;
  }

  @Override
  public void close() {
    workerExecutor.close();
  }
}
