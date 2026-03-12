package org.chimera.orchestrator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryQueueGovernanceMetrics implements QueueGovernanceMetrics {
  private final Map<String, Map<LocalDate, DailyCounters>> countersByTenant =
      new ConcurrentHashMap<>();

  @Override
  public void recordRetry(String tenantId, UUID taskId) {
    validateTenant(tenantId);
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
    countersFor(tenantId, LocalDate.now(java.time.ZoneOffset.UTC)).retries.incrementAndGet();
  }

  @Override
  public void recordDeadLetter(String tenantId, UUID taskId) {
    validateTenant(tenantId);
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
    countersFor(tenantId, LocalDate.now(java.time.ZoneOffset.UTC)).deadLetters.incrementAndGet();
  }

  @Override
  public void recordExecutionLatency(
      String tenantId, UUID taskId, long executionLatencyMs, boolean success) {
    validateTenant(tenantId);
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (executionLatencyMs < 0) {
      throw new IllegalArgumentException("executionLatencyMs must be >= 0");
    }
    DailyCounters counters = countersFor(tenantId, LocalDate.now(java.time.ZoneOffset.UTC));
    counters.executionLatenciesMs.add(executionLatencyMs);
    if (success) {
      counters.successfulExecutions.incrementAndGet();
    } else {
      counters.failedExecutions.incrementAndGet();
    }
  }

  @Override
  public QueueGovernanceSnapshot snapshot(String tenantId, LocalDate date) {
    validateTenant(tenantId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    DailyCounters counters =
        countersByTenant.getOrDefault(tenantId, Map.of()).getOrDefault(date, DailyCounters.ZERO);
    List<Long> latencies = counters.copyLatencies();
    return new QueueGovernanceSnapshot(
        counters.retries.get(),
        counters.deadLetters.get(),
        percentile(latencies, 0.50),
        percentile(latencies, 0.95),
        counters.successfulExecutions.get(),
        counters.failedExecutions.get());
  }

  private DailyCounters countersFor(String tenantId, LocalDate date) {
    Map<LocalDate, DailyCounters> byDate =
        countersByTenant.computeIfAbsent(tenantId, ignored -> new ConcurrentHashMap<>());
    return byDate.computeIfAbsent(date, ignored -> new DailyCounters());
  }

  private static void validateTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
  }

  private static final class DailyCounters {
    static final DailyCounters ZERO = new DailyCounters();

    final AtomicInteger retries = new AtomicInteger();
    final AtomicInteger deadLetters = new AtomicInteger();
    final AtomicInteger successfulExecutions = new AtomicInteger();
    final AtomicInteger failedExecutions = new AtomicInteger();
    final List<Long> executionLatenciesMs = Collections.synchronizedList(new ArrayList<>());

    private List<Long> copyLatencies() {
      synchronized (executionLatenciesMs) {
        return List.copyOf(executionLatenciesMs);
      }
    }
  }

  private static long percentile(List<Long> latencies, double quantile) {
    if (latencies == null || latencies.isEmpty()) {
      return 0L;
    }
    List<Long> sorted = new ArrayList<>(latencies);
    sorted.sort(Long::compareTo);
    int index = (int) Math.ceil(quantile * sorted.size()) - 1;
    if (index < 0) {
      index = 0;
    }
    if (index >= sorted.size()) {
      index = sorted.size() - 1;
    }
    return sorted.get(index);
  }
}
