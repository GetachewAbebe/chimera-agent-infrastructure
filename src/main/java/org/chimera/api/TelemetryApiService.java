package org.chimera.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.infrastructure.queue.QueuePort;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;
import org.chimera.orchestrator.InMemoryQueueGovernanceMetrics;
import org.chimera.orchestrator.QueueGovernanceMetrics;
import org.chimera.orchestrator.QueueGovernanceSnapshot;
import org.chimera.persistence.InMemoryTrendSignalRepository;
import org.chimera.persistence.TaskRepository;
import org.chimera.persistence.TrendSignalRepository;
import org.chimera.persistence.WalletLedgerRepository;

public final class TelemetryApiService {
  private final TaskRepository taskRepository;
  private final QueuePort<Task> taskQueue;
  private final QueuePort<UUID> reviewQueue;
  private final QueuePort<UUID> deadLetterQueue;
  private final WalletLedgerRepository walletLedgerRepository;
  private final TrendSignalRepository trendSignalRepository;
  private final QueueGovernanceMetrics queueGovernanceMetrics;
  private final String queueBackend;
  private final String walletProvider;
  private final BigDecimal dailyBudgetUsd;

  public TelemetryApiService(
      TaskRepository taskRepository,
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      WalletLedgerRepository walletLedgerRepository,
      String queueBackend,
      String walletProvider,
      BigDecimal dailyBudgetUsd) {
    this(
        taskRepository,
        taskQueue,
        reviewQueue,
        new InMemoryQueuePort<>(),
        walletLedgerRepository,
        new InMemoryTrendSignalRepository(),
        new InMemoryQueueGovernanceMetrics(),
        queueBackend,
        walletProvider,
        dailyBudgetUsd);
  }

  public TelemetryApiService(
      TaskRepository taskRepository,
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      QueuePort<UUID> deadLetterQueue,
      WalletLedgerRepository walletLedgerRepository,
      QueueGovernanceMetrics queueGovernanceMetrics,
      String queueBackend,
      String walletProvider,
      BigDecimal dailyBudgetUsd) {
    this(
        taskRepository,
        taskQueue,
        reviewQueue,
        deadLetterQueue,
        walletLedgerRepository,
        new InMemoryTrendSignalRepository(),
        queueGovernanceMetrics,
        queueBackend,
        walletProvider,
        dailyBudgetUsd);
  }

  public TelemetryApiService(
      TaskRepository taskRepository,
      QueuePort<Task> taskQueue,
      QueuePort<UUID> reviewQueue,
      QueuePort<UUID> deadLetterQueue,
      WalletLedgerRepository walletLedgerRepository,
      TrendSignalRepository trendSignalRepository,
      QueueGovernanceMetrics queueGovernanceMetrics,
      String queueBackend,
      String walletProvider,
      BigDecimal dailyBudgetUsd) {
    if (taskRepository == null) {
      throw new IllegalArgumentException("taskRepository is required");
    }
    if (taskQueue == null) {
      throw new IllegalArgumentException("taskQueue is required");
    }
    if (reviewQueue == null) {
      throw new IllegalArgumentException("reviewQueue is required");
    }
    if (deadLetterQueue == null) {
      throw new IllegalArgumentException("deadLetterQueue is required");
    }
    if (walletLedgerRepository == null) {
      throw new IllegalArgumentException("walletLedgerRepository is required");
    }
    if (trendSignalRepository == null) {
      throw new IllegalArgumentException("trendSignalRepository is required");
    }
    if (queueGovernanceMetrics == null) {
      throw new IllegalArgumentException("queueGovernanceMetrics is required");
    }
    this.taskRepository = taskRepository;
    this.taskQueue = taskQueue;
    this.reviewQueue = reviewQueue;
    this.deadLetterQueue = deadLetterQueue;
    this.walletLedgerRepository = walletLedgerRepository;
    this.trendSignalRepository = trendSignalRepository;
    this.queueGovernanceMetrics = queueGovernanceMetrics;
    this.queueBackend = queueBackend == null || queueBackend.isBlank() ? "in-memory" : queueBackend;
    this.walletProvider =
        walletProvider == null || walletProvider.isBlank() ? "unknown" : walletProvider;
    this.dailyBudgetUsd = dailyBudgetUsd == null ? BigDecimal.ZERO : dailyBudgetUsd;
  }

  public TelemetrySnapshot snapshot(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }

    var tasks = taskRepository.listByTenant(tenantId);
    Map<TaskStatus, Long> counts =
        tasks.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Task::status,
                    () -> new EnumMap<>(TaskStatus.class),
                    java.util.stream.Collectors.counting()));

    Map<String, Long> statusCounts =
        counts.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    entry -> entry.getKey().name(),
                    Map.Entry::getValue,
                    (left, right) -> right,
                    java.util.LinkedHashMap::new));

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    BigDecimal todaySpendUsd = walletLedgerRepository.sumForTenantOnDate(tenantId, today);
    BigDecimal yesterdaySpendUsd =
        walletLedgerRepository.sumForTenantOnDate(tenantId, today.minusDays(1));
    int walletTransfersToday = walletLedgerRepository.countForTenantOnDate(tenantId, today);
    int trendSignalsToday = trendSignalRepository.countForTenantOnDate(tenantId, today);
    var topTrendTopicsToday =
        trendSignalRepository.topSignalsForTenantOnDate(tenantId, today, 3).stream()
            .map(signal -> signal.topic())
            .distinct()
            .toList();
    QueueGovernanceSnapshot queueGovernanceSnapshot =
        queueGovernanceMetrics.snapshot(tenantId, today);
    BigDecimal remainingBudgetUsd = clampNonNegative(dailyBudgetUsd.subtract(todaySpendUsd));
    BigDecimal spendDeltaVsYesterdayUsd = todaySpendUsd.subtract(yesterdaySpendUsd);

    return new TelemetrySnapshot(
        tenantId,
        taskQueue.size(),
        reviewQueue.size(),
        deadLetterQueue.size(),
        tasks.size(),
        statusCounts,
        queueBackend,
        walletProvider,
        dailyBudgetUsd,
        todaySpendUsd,
        remainingBudgetUsd,
        walletTransfersToday,
        spendDeltaVsYesterdayUsd,
        trendSignalsToday,
        topTrendTopicsToday,
        queueGovernanceSnapshot.workerP50LatencyMs(),
        queueGovernanceSnapshot.workerP95LatencyMs(),
        queueGovernanceSnapshot.successfulExecutions(),
        queueGovernanceSnapshot.failedExecutions(),
        queueGovernanceSnapshot.retryAttempts(),
        queueGovernanceSnapshot.deadLetteredTasks());
  }

  private static BigDecimal clampNonNegative(BigDecimal value) {
    if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      return BigDecimal.ZERO;
    }
    return value;
  }
}
