package org.chimera.api;

import java.util.UUID;
import org.chimera.model.ReviewDecision;
import org.chimera.model.ReviewOutcome;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;
import org.chimera.persistence.TaskRepository;

public final class ReviewApiService {
  private final TaskRepository taskRepository;

  public ReviewApiService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  public ReviewDecision approve(String tenantId, UUID taskId) {
    Task updated = taskRepository.updateStatus(tenantId, taskId, TaskStatus.COMPLETE);
    return new ReviewDecision(
        ReviewOutcome.APPROVED,
        updated.status(),
        "Human reviewer approved task " + updated.taskId());
  }

  public ReviewDecision reject(String tenantId, UUID taskId) {
    Task updated = taskRepository.updateStatus(tenantId, taskId, TaskStatus.REJECTED);
    return new ReviewDecision(
        ReviewOutcome.REJECTED,
        updated.status(),
        "Human reviewer rejected task " + updated.taskId());
  }
}
