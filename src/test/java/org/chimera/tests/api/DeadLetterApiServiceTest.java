package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.chimera.api.ApiException;
import org.chimera.api.DeadLetterApiService;
import org.chimera.infrastructure.queue.InMemoryQueuePort;
import org.chimera.model.DeadLetterReplayAuditEntry;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.persistence.InMemoryDeadLetterReplayAuditRepository;
import org.chimera.persistence.InMemoryTaskRepository;
import org.junit.jupiter.api.Test;

class DeadLetterApiServiceTest {

  @Test
  void shouldReplayRejectedTaskBackToPendingQueue() {
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    DeadLetterApiService service = new DeadLetterApiService(repository, taskQueue, deadLetterQueue);

    Task rejected =
        Task.pending(
                "tenant-alpha",
                TaskType.GENERATE_CONTENT,
                Priority.HIGH,
                new TaskContext("Goal", List.of("constraint"), List.of("news://source")),
                "worker-alpha")
            .withStatus(TaskStatus.REJECTED);
    repository.saveAll(List.of(rejected));
    deadLetterQueue.push(rejected.taskId());

    Task replayed = service.replay("tenant-alpha", rejected.taskId());

    assertThat(replayed.status()).isEqualTo(TaskStatus.PENDING);
    assertThat(taskQueue.size()).isEqualTo(1);
    assertThat(deadLetterQueue.size()).isZero();
  }

  @Test
  void shouldRejectReplayWhenTaskIsNotRejected() {
    InMemoryTaskRepository repository = new InMemoryTaskRepository();
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    DeadLetterApiService service = new DeadLetterApiService(repository, taskQueue, deadLetterQueue);

    Task complete =
        Task.pending(
                "tenant-alpha",
                TaskType.GENERATE_CONTENT,
                Priority.HIGH,
                new TaskContext("Goal", List.of("constraint"), List.of("news://source")),
                "worker-alpha")
            .withStatus(TaskStatus.COMPLETE);
    repository.saveAll(List.of(complete));

    assertThatThrownBy(() -> service.replay("tenant-alpha", complete.taskId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("REJECTED");
  }

  @Test
  void shouldEnforceDailyReplayLimitPerTask() {
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    InMemoryDeadLetterReplayAuditRepository auditRepository =
        new InMemoryDeadLetterReplayAuditRepository();
    Instant now = Instant.parse("2026-03-12T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    DeadLetterApiService service =
        new DeadLetterApiService(
            taskRepository, taskQueue, deadLetterQueue, auditRepository, Duration.ZERO, 2, clock);

    Task rejected =
        Task.pending(
                "tenant-alpha",
                TaskType.GENERATE_CONTENT,
                Priority.HIGH,
                new TaskContext("Goal", List.of("constraint"), List.of("news://source")),
                "worker-alpha")
            .withStatus(TaskStatus.REJECTED);
    taskRepository.saveAll(List.of(rejected));
    auditRepository.append(
        new DeadLetterReplayAuditEntry(
            null,
            "tenant-alpha",
            rejected.taskId(),
            true,
            "replay_accepted",
            Instant.parse("2026-03-12T01:00:00Z")));
    auditRepository.append(
        new DeadLetterReplayAuditEntry(
            null,
            "tenant-alpha",
            rejected.taskId(),
            true,
            "replay_accepted",
            Instant.parse("2026-03-12T04:00:00Z")));

    assertThatThrownBy(() -> service.replay("tenant-alpha", rejected.taskId()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.statusCode()).isEqualTo(429);
              assertThat(apiException.apiError().error()).isEqualTo("replay_rate_limited");
            });
  }

  @Test
  void shouldEnforceReplayCooldownBetweenAcceptedReplays() {
    InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
    InMemoryQueuePort<Task> taskQueue = new InMemoryQueuePort<>();
    InMemoryQueuePort<UUID> deadLetterQueue = new InMemoryQueuePort<>();
    InMemoryDeadLetterReplayAuditRepository auditRepository =
        new InMemoryDeadLetterReplayAuditRepository();
    Instant now = Instant.parse("2026-03-12T12:00:00Z");
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    DeadLetterApiService service =
        new DeadLetterApiService(
            taskRepository,
            taskQueue,
            deadLetterQueue,
            auditRepository,
            Duration.ofMinutes(10),
            5,
            clock);

    Task rejected =
        Task.pending(
                "tenant-alpha",
                TaskType.GENERATE_CONTENT,
                Priority.HIGH,
                new TaskContext("Goal", List.of("constraint"), List.of("news://source")),
                "worker-alpha")
            .withStatus(TaskStatus.REJECTED);
    taskRepository.saveAll(List.of(rejected));
    auditRepository.append(
        new DeadLetterReplayAuditEntry(
            null,
            "tenant-alpha",
            rejected.taskId(),
            true,
            "replay_accepted",
            Instant.parse("2026-03-12T11:55:00Z")));

    assertThatThrownBy(() -> service.replay("tenant-alpha", rejected.taskId()))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiException = (ApiException) ex;
              assertThat(apiException.statusCode()).isEqualTo(429);
              assertThat(apiException.apiError().error()).isEqualTo("replay_cooldown_active");
            });
  }
}
