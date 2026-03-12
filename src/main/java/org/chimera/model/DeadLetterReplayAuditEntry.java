package org.chimera.model;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterReplayAuditEntry(
    UUID eventId,
    String tenantId,
    UUID taskId,
    boolean accepted,
    String reason,
    Instant occurredAt) {
  public DeadLetterReplayAuditEntry {
    eventId = eventId == null ? UUID.randomUUID() : eventId;
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }
}
