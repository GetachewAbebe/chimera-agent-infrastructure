package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.chimera.model.Priority;
import org.chimera.model.Task;
import org.chimera.model.TaskContext;
import org.chimera.model.TaskStatus;
import org.chimera.model.TaskType;
import org.chimera.persistence.JdbcTaskRepository;
import org.junit.jupiter.api.Test;

class JdbcTaskRepositoryTest {
  private static final String TENANT_ALPHA = "tenant-alpha";
  private static final String TENANT_BETA = "tenant-beta";

  @Test
  void shouldPersistAndListTasks() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcTaskRepository repository = new JdbcTaskRepository(dataSource, new ObjectMapper());

      Task task =
          new Task(
              UUID.randomUUID(),
              TENANT_ALPHA,
              TaskType.GENERATE_CONTENT,
              Priority.HIGH,
              new TaskContext(
                  "Generate high-engagement teaser",
                  List.of("Respect disclosure policy"),
                  List.of("news://ethiopia/fashion/trends")),
              "worker-delta",
              Instant.now(),
              TaskStatus.PENDING);

      repository.saveAll(List.of(task));

      List<Task> tasks = repository.listByTenant(TENANT_ALPHA);
      assertThat(tasks).hasSize(1);
      assertThat(tasks.getFirst().taskId()).isEqualTo(task.taskId());
      assertThat(tasks.getFirst().context().goalDescription()).contains("high-engagement");
    }
  }

  @Test
  void shouldUpdateTaskStatus() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcTaskRepository repository = new JdbcTaskRepository(dataSource, new ObjectMapper());

      Task task =
          new Task(
              UUID.randomUUID(),
              TENANT_ALPHA,
              TaskType.REPLY_COMMENT,
              Priority.MEDIUM,
              new TaskContext(
                  "Reply to top comments",
                  List.of("No unsafe claims"),
                  List.of("twitter://mentions/recent")),
              "worker-epsilon",
              Instant.now(),
              TaskStatus.PENDING);

      repository.saveAll(List.of(task));
      repository.updateStatus(TENANT_ALPHA, task.taskId(), TaskStatus.COMPLETE);

      Task updated = repository.findByTenant(TENANT_ALPHA, task.taskId()).orElseThrow();
      assertThat(updated.status()).isEqualTo(TaskStatus.COMPLETE);
    }
  }

  @Test
  void shouldEnforceTenantScopedReads() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcTaskRepository repository = new JdbcTaskRepository(dataSource, new ObjectMapper());

      Task alphaTask =
          new Task(
              UUID.randomUUID(),
              TENANT_ALPHA,
              TaskType.GENERATE_CONTENT,
              Priority.HIGH,
              new TaskContext(
                  "Alpha campaign",
                  List.of("Respect disclosure policy"),
                  List.of("news://ethiopia/fashion/trends")),
              "worker-alpha",
              Instant.now(),
              TaskStatus.PENDING);

      Task betaTask =
          new Task(
              UUID.randomUUID(),
              TENANT_BETA,
              TaskType.GENERATE_CONTENT,
              Priority.HIGH,
              new TaskContext(
                  "Beta campaign",
                  List.of("Respect disclosure policy"),
                  List.of("news://ethiopia/fashion/trends")),
              "worker-beta",
              Instant.now(),
              TaskStatus.PENDING);

      repository.saveAll(List.of(alphaTask, betaTask));

      assertThat(repository.listByTenant(TENANT_ALPHA)).hasSize(1);
      assertThat(repository.listByTenant(TENANT_ALPHA).getFirst().tenantId())
          .isEqualTo(TENANT_ALPHA);
      assertThat(repository.findByTenant(TENANT_ALPHA, betaTask.taskId())).isEmpty();
    }
  }

  private HikariDataSource createTestDataSource() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:chimera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    HikariDataSource dataSource = new HikariDataSource(config);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS api_tasks");
      statement.execute(
          """
          CREATE TABLE api_tasks (
              task_id UUID PRIMARY KEY,
              tenant_id TEXT NOT NULL,
              task_type TEXT NOT NULL,
              priority TEXT NOT NULL,
              context_json TEXT NOT NULL,
              assigned_worker_id TEXT NOT NULL,
              created_at TIMESTAMP NOT NULL,
              status TEXT NOT NULL
          )
          """);
    }

    return dataSource;
  }
}
