package org.chimera.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.chimera.model.DeadLetterReplayAuditEntry;

public interface DeadLetterReplayAuditRepository {
  void append(DeadLetterReplayAuditEntry entry);

  int countAcceptedForTaskOnDate(String tenantId, UUID taskId, LocalDate date);

  Optional<DeadLetterReplayAuditEntry> findLatestAcceptedForTask(String tenantId, UUID taskId);
}
