package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.chimera.api.TelemetryApiService;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.model.TrendSignal;
import org.chimera.model.WalletLedgerEntry;
import org.chimera.orchestrator.InMemoryQueueGovernanceMetrics;
import org.chimera.persistence.InMemoryTaskRepository;
import org.chimera.persistence.InMemoryTrendSignalRepository;
import org.chimera.persistence.InMemoryWalletLedgerRepository;
import org.junit.jupiter.api.Test;

class TelemetryApiServiceTest {

  @Test
  void shouldBuildTenantTelemetrySnapshot() {
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> reviewQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    InMemoryWalletLedgerRepository walletLedgerRepository = new InMemoryWalletLedgerRepository();
    InMemoryTrendSignalRepository trendSignalRepository = new InMemoryTrendSignalRepository();
    InMemoryQueueGovernanceMetrics queueGovernanceMetrics = new InMemoryQueueGovernanceMetrics();

    Task task =
        Task.pending(
            "tenant-alpha",
            TaskType.GENERATE_CONTENT,
            Priority.HIGH,
            new TaskContext("Goal", List.of("constraint"), List.of("news://source")),
            "worker-alpha");
    repository.saveAll(List.of(task.withStatus(TaskStatus.ESCALATED)));
    taskQueue.push(task);
    reviewQueue.push(task.taskId());
    deadLetterQueue.push(task.taskId());
    queueGovernanceMetrics.recordRetry("tenant-alpha", task.taskId());
    queueGovernanceMetrics.recordRetry("tenant-alpha", task.taskId());
    queueGovernanceMetrics.recordDeadLetter("tenant-alpha", task.taskId());
    queueGovernanceMetrics.recordExecutionLatency("tenant-alpha", task.taskId(), 120, true);
    queueGovernanceMetrics.recordExecutionLatency("tenant-alpha", task.taskId(), 250, false);
    queueGovernanceMetrics.recordExecutionLatency("tenant-alpha", task.taskId(), 80, true);
    walletLedgerRepository.append(
        new WalletLedgerEntry(
            null,
            "tenant-alpha",
            task.taskId(),
            task.assignedWorkerId(),
            new BigDecimal("40.50"),
            "simulated",
            "sim-tx-1",
            Instant.now()));
    walletLedgerRepository.append(
        new WalletLedgerEntry(
            null,
            "tenant-alpha",
            task.taskId(),
            task.assignedWorkerId(),
            new BigDecimal("10.00"),
            "simulated",
            "sim-tx-prev",
            LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
    trendSignalRepository.append(
        "tenant-alpha",
        new TrendSignal(
            "sustainable-streetwear", 0.88, "news://ethiopia/fashion/trends", Instant.now()));
    trendSignalRepository.append(
        "tenant-alpha",
        new TrendSignal("creator-collab", 0.81, "twitter://mentions/recent", Instant.now()));

    TelemetryApiService service =
        new TelemetryApiService(
            repository,
            taskQueue,
            reviewQueue,
            deadLetterQueue,
            walletLedgerRepository,
            trendSignalRepository,
            queueGovernanceMetrics,
            "in-memory",
            "simulated",
            new BigDecimal("500.00"));

    var snapshot = service.snapshot("tenant-alpha");
    assertThat(snapshot.tenantId()).isEqualTo("tenant-alpha");
    assertThat(snapshot.totalTasks()).isEqualTo(1);
    assertThat(snapshot.taskQueueDepth()).isEqualTo(1);
    assertThat(snapshot.reviewQueueDepth()).isEqualTo(1);
    assertThat(snapshot.deadLetterQueueDepth()).isEqualTo(1);
    assertThat(snapshot.statusCounts()).containsEntry("ESCALATED", 1L);
    assertThat(snapshot.walletProvider()).isEqualTo("simulated");
    assertThat(snapshot.queueBackend()).isEqualTo("in-memory");
    assertThat(snapshot.todaySpendUsd()).isEqualByComparingTo("40.50");
    assertThat(snapshot.remainingBudgetUsd()).isEqualByComparingTo("459.50");
    assertThat(snapshot.walletTransfersToday()).isEqualTo(1);
    assertThat(snapshot.spendDeltaVsYesterdayUsd()).isEqualByComparingTo("30.50");
    assertThat(snapshot.trendSignalsToday()).isEqualTo(2);
    assertThat(snapshot.topTrendTopicsToday()).contains("sustainable-streetwear", "creator-collab");
    assertThat(snapshot.workerP50LatencyMs()).isEqualTo(120);
    assertThat(snapshot.workerP95LatencyMs()).isEqualTo(250);
    assertThat(snapshot.successfulExecutionsToday()).isEqualTo(2);
    assertThat(snapshot.failedExecutionsToday()).isEqualTo(1);
    assertThat(snapshot.retryAttemptsToday()).isEqualTo(2);
    assertThat(snapshot.deadLetteredTasksToday()).isEqualTo(1);
  }
}
