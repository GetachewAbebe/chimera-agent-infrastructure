package org.chimera.persistence;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;

public final class InMemoryTaskRepository implements TaskRepository {
  private final Map<UUID, Task> tasksById = new ConcurrentHashMap<>();

  @Override
  public void saveAll(Collection<Task> tasks) {
    tasks.forEach(task -> tasksById.put(task.taskId(), task));
  }

  @Override
  public List<Task> listByTenant(String tenantId) {
    return tasksById.values().stream()
        .filter(task -> task.tenantId().equals(tenantId))
        .sorted(Comparator.comparing(Task::createdAt))
        .toList();
  }

  @Override
  public Optional<Task> findByTenant(String tenantId, UUID taskId) {
    return Optional.ofNullable(tasksById.get(taskId))
        .filter(task -> task.tenantId().equals(tenantId));
  }

  @Override
  public Task updateStatus(String tenantId, UUID taskId, TaskStatus status) {
    Task existing =
        findByTenant(tenantId, taskId)
            .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));

    Task updated = existing.withStatus(status);
    tasksById.put(taskId, updated);
    return updated;
  }
}
