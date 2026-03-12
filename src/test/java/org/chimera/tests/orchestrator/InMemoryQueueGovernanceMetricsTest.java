package org.chimera.tests.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.chimera.orchestrator.InMemoryQueueGovernanceMetrics;
import org.junit.jupiter.api.Test;

class InMemoryQueueGovernanceMetricsTest {

  @Test
  void shouldAggregateRetriesDeadLettersAndLatencyPercentiles() {
    InMemoryQueueGovernanceMetrics metrics = new InMemoryQueueGovernanceMetrics();
    UUID taskId = UUID.randomUUID();

    metrics.recordRetry("tenant-alpha", taskId);
    metrics.recordRetry("tenant-alpha", taskId);
    metrics.recordDeadLetter("tenant-alpha", taskId);
    metrics.recordExecutionLatency("tenant-alpha", taskId, 80, true);
    metrics.recordExecutionLatency("tenant-alpha", taskId, 120, false);
    metrics.recordExecutionLatency("tenant-alpha", taskId, 200, true);
    metrics.recordExecutionLatency("tenant-alpha", taskId, 300, true);

    var snapshot = metrics.snapshot("tenant-alpha", LocalDate.now(ZoneOffset.UTC));

    assertThat(snapshot.retryAttempts()).isEqualTo(2);
    assertThat(snapshot.deadLetteredTasks()).isEqualTo(1);
    assertThat(snapshot.workerP50LatencyMs()).isEqualTo(120);
    assertThat(snapshot.workerP95LatencyMs()).isEqualTo(300);
    assertThat(snapshot.successfulExecutions()).isEqualTo(3);
    assertThat(snapshot.failedExecutions()).isEqualTo(1);
  }
}
