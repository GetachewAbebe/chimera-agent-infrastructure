package org.chimera.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.chimera.infrastructure.queue.QueuePort;
import org.chimera.model.DeadLetterReplayAuditEntry;
import org.chimera.model.Task;
import org.chimera.model.TaskStatus;
import org.chimera.persistence.DeadLetterReplayAuditRepository;
import org.chimera.persistence.InMemoryDeadLetterReplayAuditRepository;
import org.chimera.persistence.TaskRepository;

public final class DeadLetterApiService {
  private static final Duration DEFAULT_REPLAY_COOLDOWN = Duration.ofMinutes(5);
  private static final int DEFAULT_MAX_REPLAYS_PER_TASK_PER_DAY = 3;

  private final TaskRepository taskRepository;
  private final QueuePort<Task> taskQueue;
  private final QueuePort<UUID> deadLetterQueue;
  private final DeadLetterReplayAuditRepository replayAuditRepository;
  private final Duration replayCooldown;
  private final int maxReplaysPerTaskPerDay;
  private final Clock clock;

  public DeadLetterApiService(
      TaskRepository taskRepository, QueuePort<Task> taskQueue, QueuePort<UUID> deadLetterQueue) {
    this(
        taskRepository,
        taskQueue,
        deadLetterQueue,
        new InMemoryDeadLetterReplayAuditRepository(),
        DEFAULT_REPLAY_COOLDOWN,
        DEFAULT_MAX_REPLAYS_PER_TASK_PER_DAY,
        Clock.systemUTC());
  }

  public DeadLetterApiService(
      TaskRepository taskRepository,
      QueuePort<Task> taskQueue,
      QueuePort<UUID> deadLetterQueue,
      DeadLetterReplayAuditRepository replayAuditRepository,
      Duration replayCooldown,
      int maxReplaysPerTaskPerDay) {
    this(
        taskRepository,
        taskQueue,
        deadLetterQueue,
        replayAuditRepository,
        replayCooldown,
        maxReplaysPerTaskPerDay,
        Clock.systemUTC());
  }

  public DeadLetterApiService(
      TaskRepository taskRepository,
      QueuePort<Task> taskQueue,
      QueuePort<UUID> deadLetterQueue,
      DeadLetterReplayAuditRepository replayAuditRepository,
      Duration replayCooldown,
      int maxReplaysPerTaskPerDay,
      Clock clock) {
    if (taskRepository == null) {
      throw new IllegalArgumentException("taskRepository is required");
    }
    if (taskQueue == null) {
      throw new IllegalArgumentException("taskQueue is required");
    }
    if (deadLetterQueue == null) {
      throw new IllegalArgumentException("deadLetterQueue is required");
    }
    if (replayAuditRepository == null) {
      throw new IllegalArgumentException("replayAuditRepository is required");
    }
    if (replayCooldown == null || replayCooldown.isNegative()) {
      throw new IllegalArgumentException("replayCooldown must be >= 0");
    }
    if (maxReplaysPerTaskPerDay < 1) {
      throw new IllegalArgumentException("maxReplaysPerTaskPerDay must be >= 1");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock is required");
    }
    this.taskRepository = taskRepository;
    this.taskQueue = taskQueue;
    this.deadLetterQueue = deadLetterQueue;
    this.replayAuditRepository = replayAuditRepository;
    this.replayCooldown = replayCooldown;
    this.maxReplaysPerTaskPerDay = maxReplaysPerTaskPerDay;
    this.clock = clock;
  }

  public Task replay(String tenantId, UUID taskId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }

    Task task =
        taskRepository
            .findByTenant(tenantId, taskId)
            .orElseThrow(
                () -> {
                  appendReplayAudit(tenantId, taskId, false, "task_not_found");
                  return new IllegalArgumentException("task not found: " + taskId);
                });

    if (task.status() != TaskStatus.REJECTED) {
      appendReplayAudit(tenantId, taskId, false, "invalid_task_status_" + task.status().name());
      throw new IllegalStateException("Only REJECTED tasks can be replayed");
    }

    enforceReplayPolicy(tenantId, task.taskId());

    try {
      removeFromDeadLetterQueue(task.taskId());
      taskQueue.push(task.withStatus(TaskStatus.PENDING));
      Task updated = taskRepository.updateStatus(tenantId, task.taskId(), TaskStatus.PENDING);
      appendReplayAudit(tenantId, task.taskId(), true, "replay_accepted");
      return updated;
    } catch (RuntimeException ex) {
      appendReplayAudit(tenantId, task.taskId(), false, "replay_failed");
      throw ex;
    }
  }

  private void removeFromDeadLetterQueue(UUID taskId) {
    int size = deadLetterQueue.size();
    if (size <= 0) {
      return;
    }

    boolean removed = false;
    List<UUID> retained = new ArrayList<>();
    for (int index = 0; index < size; index++) {
      UUID current = deadLetterQueue.poll().orElse(null);
      if (current == null) {
        break;
      }
      if (!removed && taskId.equals(current)) {
        removed = true;
      } else {
        retained.add(current);
      }
    }

    retained.forEach(deadLetterQueue::push);
  }

  private void enforceReplayPolicy(String tenantId, UUID taskId) {
    Instant now = Instant.now(clock);
    LocalDate utcDate = now.atOffset(java.time.ZoneOffset.UTC).toLocalDate();
    int acceptedReplaysToday =
        replayAuditRepository.countAcceptedForTaskOnDate(tenantId, taskId, utcDate);
    if (acceptedReplaysToday >= maxReplaysPerTaskPerDay) {
      appendReplayAudit(tenantId, taskId, false, "daily_limit_exceeded");
      throw new ApiException(
          429,
          "replay_rate_limited",
          "Replay limit reached for task "
              + taskId
              + " on "
              + utcDate
              + " (max "
              + maxReplaysPerTaskPerDay
              + " per day)");
    }

    if (replayCooldown.isZero()) {
      return;
    }

    replayAuditRepository
        .findLatestAcceptedForTask(tenantId, taskId)
        .ifPresent(
            lastAccepted -> {
              Instant replayEligibleAt = lastAccepted.occurredAt().plus(replayCooldown);
              if (now.isBefore(replayEligibleAt)) {
                long remainingSeconds =
                    Math.max(1, Duration.between(now, replayEligibleAt).toSeconds());
                appendReplayAudit(tenantId, taskId, false, "cooldown_active");
                throw new ApiException(
                    429,
                    "replay_cooldown_active",
                    "Replay cooldown active for task "
                        + taskId
                        + ", retry in "
                        + remainingSeconds
                        + "s");
              }
            });
  }

  private void appendReplayAudit(String tenantId, UUID taskId, boolean accepted, String reason) {
    replayAuditRepository.append(
        new DeadLetterReplayAuditEntry(
            null, tenantId, taskId, accepted, reason, Instant.now(clock)));
  }
}
