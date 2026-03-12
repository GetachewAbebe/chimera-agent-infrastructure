package org.chimera.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;

public interface TaskRepository {
  void saveAll(Collection<Task> tasks);

  List<Task> listByTenant(String tenantId);

  Optional<Task> findByTenant(String tenantId, UUID taskId);

  Task updateStatus(String tenantId, UUID taskId, TaskStatus status);
}
