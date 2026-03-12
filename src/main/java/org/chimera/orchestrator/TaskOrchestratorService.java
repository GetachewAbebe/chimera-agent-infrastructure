package org.chimera.orchestrator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.infrastructure.queue.QueuePort;
import org.chimera.judge.JudgeService;
import org.chimera.model.GlobalStateSnapshot;
import org.chimera.model.ReviewDecision;
import org.chimera.model.Task;
import org.chimera.model.TaskResult;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.model.WalletLedgerEntry;
import org.chimera.persistence.InMemoryWalletLedgerRepository;
import org.chimera.persistence.TaskRepository;
import org.chimera.persistence.WalletLedgerRepository;
import org.chimera.security.SensitiveTopicClassifier;
import org.chimera.wallet.WalletTaskResourceParser;
import org.chimera.worker.WorkerService;

public final class TaskOrchestratorService {
  private static final int DEFAULT_MAX_RETRY_ATTEMPTS = 2;

  private final QueuePort<Task> taskQueue;
  private final QueuePort<UUID> reviewQueue;
  private final QueuePort<UUID> deadLetterQueue;
  private final TaskRepository taskRepository;
  private final WorkerService workerService;
  private final JudgeService judgeService;
  private final BigDecimal defaultDailyBudgetUsd;
  private final WalletLedgerRepository walletLedgerRepository;
  private final QueueGovernanceMetrics queueGovernanceMetrics;
  private final SensitiveTopicClassifier sensitiveTopicClassifier;
  private final int maxRetryAttempts;
  private final Map<UUID, Integer> retryAttemptsByTask = new ConcurrentHashMap<>();

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService) {
    this(
        taskQueue,
        null,
        new InMemoryQueuePort<>(),
        taskRepository,
        workerService,
        judgeService,
        new BigDecimal("500.00"),
        new InMemoryWalletLedgerRepository(),
        new InMemoryQueueGovernanceMetrics(),
        DEFAULT_MAX_RETRY_ATTEMPTS);
  }

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService) {
    this(
        taskQueue,
        reviewQueue,
        new InMemoryQueuePort<>(),
        taskRepository,
        workerService,
        judgeService,
        new BigDecimal("500.00"),
        new InMemoryWalletLedgerRepository(),
        new InMemoryQueueGovernanceMetrics(),
        DEFAULT_MAX_RETRY_ATTEMPTS);
  }

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService,
      BigDecimal defaultDailyBudgetUsd) {
    this(
        taskQueue,
        reviewQueue,
        new InMemoryQueuePort<>(),
        taskRepository,
        workerService,
        judgeService,
        defaultDailyBudgetUsd,
        new InMemoryWalletLedgerRepository(),
        new InMemoryQueueGovernanceMetrics(),
        DEFAULT_MAX_RETRY_ATTEMPTS);
  }

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService,
      BigDecimal defaultDailyBudgetUsd,
      WalletLedgerRepository walletLedgerRepository) {
    this(
        taskQueue,
        reviewQueue,
        new InMemoryQueuePort<>(),
        taskRepository,
        workerService,
        judgeService,
        defaultDailyBudgetUsd,
        walletLedgerRepository,
        new InMemoryQueueGovernanceMetrics(),
        DEFAULT_MAX_RETRY_ATTEMPTS);
  }

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      QueuePort<UUID> deadLetterQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService,
      BigDecimal defaultDailyBudgetUsd,
      WalletLedgerRepository walletLedgerRepository) {
    this(
        taskQueue,
        reviewQueue,
        deadLetterQueue,
        taskRepository,
        workerService,
        judgeService,
        defaultDailyBudgetUsd,
        walletLedgerRepository,
        new InMemoryQueueGovernanceMetrics(),
        DEFAULT_MAX_RETRY_ATTEMPTS);
  }

  public TaskOrchestratorService(
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      QueuePort<UUID> deadLetterQueue,
      TaskRepository taskRepository,
      WorkerService workerService,
      JudgeService judgeService,
      BigDecimal defaultDailyBudgetUsd,
      WalletLedgerRepository walletLedgerRepository,
      QueueGovernanceMetrics queueGovernanceMetrics,
      int maxRetryAttempts) {
    if (taskQueue == null) {
      throw new IllegalArgumentException("taskQueue is required");
    }
    if (deadLetterQueue == null) {
      throw new IllegalArgumentException("deadLetterQueue is required");
    }
    if (taskRepository == null) {
      throw new IllegalArgumentException("taskRepository is required");
    }
    if (workerService == null) {
      throw new IllegalArgumentException("workerService is required");
    }
    if (judgeService == null) {
      throw new IllegalArgumentException("judgeService is required");
    }
    if (defaultDailyBudgetUsd == null || defaultDailyBudgetUsd.signum() <= 0) {
      throw new IllegalArgumentException("defaultDailyBudgetUsd must be > 0");
    }
    if (walletLedgerRepository == null) {
      throw new IllegalArgumentException("walletLedgerRepository is required");
    }
    if (queueGovernanceMetrics == null) {
      throw new IllegalArgumentException("queueGovernanceMetrics is required");
    }
    if (maxRetryAttempts < 0) {
      throw new IllegalArgumentException("maxRetryAttempts must be >= 0");
    }

    this.taskQueue = taskQueue;
    this.reviewQueue = reviewQueue;
    this.deadLetterQueue = deadLetterQueue;
    this.taskRepository = taskRepository;
    this.workerService = workerService;
    this.judgeService = judgeService;
    this.defaultDailyBudgetUsd = defaultDailyBudgetUsd;
    this.walletLedgerRepository = walletLedgerRepository;
    this.queueGovernanceMetrics = queueGovernanceMetrics;
    this.sensitiveTopicClassifier = new SensitiveTopicClassifier();
    this.maxRetryAttempts = maxRetryAttempts;
  }

  public List<Task> processAvailableTasks(int maxTasks) {
    if (maxTasks < 1) {
      throw new IllegalArgumentException("maxTasks must be >= 1");
    }

    List<Task> processed = new ArrayList<>();
    for (int index = 0; index < maxTasks; index++) {
      Task queuedTask = taskQueue.poll().orElse(null);
      if (queuedTask == null) {
        break;
      }
      processed.add(processOne(queuedTask));
    }
    return List.copyOf(processed);
  }

  private Task processOne(Task queuedTask) {
    long startedAtNanos = System.nanoTime();
    Task inProgress =
        taskRepository.updateStatus(
            queuedTask.tenantId(), queuedTask.taskId(), TaskStatus.IN_PROGRESS);

    try {
      TaskResult result = workerService.execute(inProgress).join();
      queueGovernanceMetrics.recordExecutionLatency(
          inProgress.tenantId(),
          inProgress.taskId(),
          elapsedMillis(startedAtNanos),
          result.success());
      GlobalStateSnapshot snapshot =
          new GlobalStateSnapshot(
              0L,
              false,
              walletLedgerRepository.sumForTenantOnDate(
                  inProgress.tenantId(), LocalDate.now(ZoneOffset.UTC)),
              defaultDailyBudgetUsd);
      ReviewDecision decision =
          judgeService.review(
              inProgress,
              result,
              snapshot,
              0L,
              sensitiveTopicClassifier.containsSensitiveTopic(
                  inProgress.context().goalDescription()));
      recordSuccessfulTransaction(inProgress, result, decision);
      if (reviewQueue != null && decision.nextStatus() == TaskStatus.ESCALATED) {
        reviewQueue.push(inProgress.taskId());
      }
      retryAttemptsByTask.remove(inProgress.taskId());
      return taskRepository.updateStatus(
          inProgress.tenantId(), inProgress.taskId(), decision.nextStatus());
    } catch (Exception ex) {
      queueGovernanceMetrics.recordExecutionLatency(
          inProgress.tenantId(), inProgress.taskId(), elapsedMillis(startedAtNanos), false);
      return handleExecutionFailure(inProgress);
    }
  }

  private static long elapsedMillis(long startedAtNanos) {
    long elapsedNanos = System.nanoTime() - startedAtNanos;
    return Math.max(0L, elapsedNanos / 1_000_000L);
  }

  private void recordSuccessfulTransaction(Task task, TaskResult result, ReviewDecision decision) {
    if (task.taskType() != TaskType.EXECUTE_TRANSACTION) {
      return;
    }
    if (decision.nextStatus() != TaskStatus.COMPLETE || !result.success()) {
      return;
    }

    String transactionId =
        result.payload() == null || result.payload().isBlank()
            ? "missing-transaction-id-" + task.taskId()
            : result.payload();
    walletLedgerRepository.append(
        new WalletLedgerEntry(
            null,
            task.tenantId(),
            task.taskId(),
            task.assignedWorkerId(),
            WalletTaskResourceParser.parseAmountUsd(task.context()),
            providerFromReasoning(result.reasoningTrace()),
            transactionId,
            result.completedAt()));
  }

  private static String providerFromReasoning(String reasoningTrace) {
    if (reasoningTrace == null || reasoningTrace.isBlank()) {
      return "unknown";
    }
    String firstToken = reasoningTrace.trim().split("\\s+", 2)[0];
    return firstToken.isBlank() ? "unknown" : firstToken.toLowerCase();
  }

  private Task handleExecutionFailure(Task inProgress) {
    int attempt = retryAttemptsByTask.merge(inProgress.taskId(), 1, Integer::sum);
    if (attempt <= maxRetryAttempts) {
      queueGovernanceMetrics.recordRetry(inProgress.tenantId(), inProgress.taskId());
      taskQueue.push(inProgress.withStatus(TaskStatus.PENDING));
      return taskRepository.updateStatus(
          inProgress.tenantId(), inProgress.taskId(), TaskStatus.PENDING);
    }

    retryAttemptsByTask.remove(inProgress.taskId());
    queueGovernanceMetrics.recordDeadLetter(inProgress.tenantId(), inProgress.taskId());
    deadLetterQueue.push(inProgress.taskId());
    return taskRepository.updateStatus(
        inProgress.tenantId(), inProgress.taskId(), TaskStatus.REJECTED);
  }
}
