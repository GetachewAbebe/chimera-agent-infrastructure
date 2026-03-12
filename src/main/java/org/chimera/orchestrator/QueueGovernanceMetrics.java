package org.chimera.orchestrator;

import java.time.LocalDate;
import java.util.UUID;

public interface QueueGovernanceMetrics {
  void recordRetry(String tenantId, UUID taskId);

  void recordDeadLetter(String tenantId, UUID taskId);

  default void recordExecutionLatency(
      String tenantId, UUID taskId, long executionLatencyMs, boolean success) {}

  QueueGovernanceSnapshot snapshot(String tenantId, LocalDate date);
}
