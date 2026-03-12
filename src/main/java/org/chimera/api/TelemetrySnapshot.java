package org.chimera.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record TelemetrySnapshot(
    String tenantId,
    int taskQueueDepth,
    int reviewQueueDepth,
    int deadLetterQueueDepth,
    int totalTasks,
    Map<String, Long> statusCounts,
    String queueBackend,
    String walletProvider,
    BigDecimal dailyBudgetUsd,
    BigDecimal todaySpendUsd,
    BigDecimal remainingBudgetUsd,
    int walletTransfersToday,
    BigDecimal spendDeltaVsYesterdayUsd,
    int trendSignalsToday,
    List<String> topTrendTopicsToday,
    long workerP50LatencyMs,
    long workerP95LatencyMs,
    int successfulExecutionsToday,
    int failedExecutionsToday,
    int retryAttemptsToday,
    int deadLetteredTasksToday) {
  public TelemetrySnapshot {
    statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
    topTrendTopicsToday =
        topTrendTopicsToday == null ? List.of() : List.copyOf(topTrendTopicsToday);
  }
}
