package org.chimera.persistence;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.chimera.model.DeadLetterReplayAuditEntry;

public final class InMemoryDeadLetterReplayAuditRepository
    implements DeadLetterReplayAuditRepository {
  private final List<DeadLetterReplayAuditEntry> entries = new CopyOnWriteArrayList<>();

  @Override
  public void append(DeadLetterReplayAuditEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is required");
    }
    entries.add(entry);
  }

  @Override
  public int countAcceptedForTaskOnDate(String tenantId, UUID taskId, LocalDate date) {
    validateTaskScope(tenantId, taskId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    return Math.toIntExact(
        entries.stream()
            .filter(entry -> entry.tenantId().equals(tenantId))
            .filter(entry -> entry.taskId().equals(taskId))
            .filter(DeadLetterReplayAuditEntry::accepted)
            .filter(entry -> toUtcDate(entry).equals(date))
            .count());
  }

  @Override
  public Optional<DeadLetterReplayAuditEntry> findLatestAcceptedForTask(
      String tenantId, UUID taskId) {
    validateTaskScope(tenantId, taskId);
    return entries.stream()
        .filter(entry -> entry.tenantId().equals(tenantId))
        .filter(entry -> entry.taskId().equals(taskId))
        .filter(DeadLetterReplayAuditEntry::accepted)
        .max(Comparator.comparing(DeadLetterReplayAuditEntry::occurredAt));
  }

  private static LocalDate toUtcDate(DeadLetterReplayAuditEntry entry) {
    return entry.occurredAt().atOffset(ZoneOffset.UTC).toLocalDate();
  }

  private static void validateTaskScope(String tenantId, UUID taskId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
  }
}
