package org.chimera.tests.openclaw;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.chimera.mcp.McpToolResult;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.openclaw.McpOpenClawStatusPublisher;
import org.junit.jupiter.api.Test;

class McpOpenClawStatusPublisherTest {

  @Test
  void shouldPublishSignedPayloadThroughOpenClawTool() {
    AtomicReference<Map<String, Object>> argsRef = new AtomicReference<>();
    McpOpenClawStatusPublisher publisher =
        new McpOpenClawStatusPublisher(
            (toolName, arguments) -> {
              assertThat(toolName).isEqualTo("openclaw.publish_status");
              argsRef.set(arguments);
              return new McpToolResult(true, "accepted", Map.of());
            });

    Task task = task("tenant-alpha", "worker-alpha");
    publisher.publishStatus(task, TaskStatus.IN_PROGRESS, "planning_to_working");

    Map<String, Object> payload = argsRef.get();
    assertThat(payload).isNotNull();
    assertThat(payload.get("agent_id")).isEqualTo("worker-alpha");
    assertThat(payload.get("tenant_id")).isEqualTo("tenant-alpha");
    assertThat(payload.get("availability")).isEqualTo("busy");
    assertThat(payload.get("safety_level")).isEqualTo("autonomous");
    assertThat(payload.get("signature")).asString().isNotBlank();
    assertThat(publisher.auditTrail()).singleElement().extracting("accepted").isEqualTo(true);
  }

  @Test
  void shouldBlockEscalatedStatesFromPublication() {
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    McpOpenClawStatusPublisher publisher =
        new McpOpenClawStatusPublisher(
            (toolName, arguments) -> {
              called.set(true);
              return new McpToolResult(true, "accepted", Map.of());
            });

    Task task = task("tenant-alpha", "worker-alpha");
    publisher.publishStatus(task, TaskStatus.ESCALATED, "judge_decision");

    assertThat(called.get()).isFalse();
    assertThat(publisher.auditTrail())
        .singleElement()
        .extracting("reason")
        .isEqualTo("blocked_unresolved_high_risk_escalation");
  }

  @Test
  void shouldKeepBoundedAuditTrail() {
    McpOpenClawStatusPublisher publisher =
        new McpOpenClawStatusPublisher(
            (toolName, arguments) -> new McpToolResult(true, "ok", Map.of()));
    Task task = task("tenant-alpha", "worker-alpha");

    for (int i = 0; i < 510; i++) {
      publisher.publishStatus(task, TaskStatus.PENDING, "tick-" + i);
    }

    List<?> audit = publisher.auditTrail();
    assertThat(audit).hasSize(500);
  }

  @Test
  void shouldRecordFailureWhenToolClientThrows() {
    McpOpenClawStatusPublisher publisher =
        new McpOpenClawStatusPublisher(
            (toolName, arguments) -> {
              throw new IllegalStateException("network-down");
            });

    publisher.publishStatus(task("tenant-alpha", "worker-alpha"), TaskStatus.COMPLETE, "done");

    assertThat(publisher.auditTrail()).singleElement().extracting("accepted").isEqualTo(false);
    assertThat(publisher.auditTrail())
        .singleElement()
        .extracting("reason")
        .asString()
        .contains("publish_failed:network-down");
  }

  private static Task task(String tenantId, String workerId) {
    return new Task(
        UUID.randomUUID(),
        tenantId,
        TaskType.GENERATE_CONTENT,
        Priority.HIGH,
        new TaskContext("launch", List.of("safe"), List.of("news://ethiopia/fashion/trends")),
        workerId,
        Instant.now(),
        TaskStatus.PENDING);
  }
}
