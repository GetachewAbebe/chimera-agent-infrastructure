package org.chimera.model;

import java.time.Instant;
import java.util.UUID;

public record Task(
    UUID taskId,
    String tenantId,
    TaskType taskType,
    Priority priority,
    TaskContext context,
    String assignedWorkerId,
    Instant createdAt,
    TaskStatus status) {

  public static Task pending(
      String tenantId,
      TaskType taskType,
      Priority priority,
      TaskContext context,
      String assignedWorkerId) {
    return new Task(
        UUID.randomUUID(),
        tenantId,
        taskType,
        priority,
        context,
        assignedWorkerId,
        Instant.now(),
        TaskStatus.PENDING);
  }

  public Task withStatus(TaskStatus newStatus) {
    return new Task(
        this.taskId(),
        this.tenantId(),
        this.taskType(),
        this.priority(),
        this.context(),
        this.assignedWorkerId(),
        this.createdAt(),
        newStatus);
  }
}
