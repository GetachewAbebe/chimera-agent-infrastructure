package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.chimera.model.DeadLetterReplayAuditEntry;
import org.chimera.persistence.JdbcDeadLetterReplayAuditRepository;
import org.junit.jupiter.api.Test;

class JdbcDeadLetterReplayAuditRepositoryTest {
  private static final String TENANT_ALPHA = "tenant-alpha";
  private static final String TENANT_BETA = "tenant-beta";

  @Test
  void shouldPersistAndQueryAcceptedReplayAudits() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcDeadLetterReplayAuditRepository repository =
          new JdbcDeadLetterReplayAuditRepository(dataSource);
      UUID taskId = UUID.randomUUID();

      repository.append(
          new DeadLetterReplayAuditEntry(
              null,
              TENANT_ALPHA,
              taskId,
              true,
              "replay_accepted",
              Instant.parse("2026-03-12T08:00:00Z")));
      repository.append(
          new DeadLetterReplayAuditEntry(
              null,
              TENANT_ALPHA,
              taskId,
              false,
              "cooldown_active",
              Instant.parse("2026-03-12T08:05:00Z")));
      repository.append(
          new DeadLetterReplayAuditEntry(
              null,
              TENANT_ALPHA,
              taskId,
              true,
              "replay_accepted",
              Instant.parse("2026-03-12T09:00:00Z")));

      assertThat(
              repository.countAcceptedForTaskOnDate(
                  TENANT_ALPHA, taskId, LocalDate.of(2026, 3, 12)))
          .isEqualTo(2);
      DeadLetterReplayAuditEntry latest =
          repository.findLatestAcceptedForTask(TENANT_ALPHA, taskId).orElseThrow();
      assertThat(latest.accepted()).isTrue();
      assertThat(latest.occurredAt()).isEqualTo(Instant.parse("2026-03-12T09:00:00Z"));
    }
  }

  @Test
  void shouldEnforceTenantAndTaskScopedQueries() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcDeadLetterReplayAuditRepository repository =
          new JdbcDeadLetterReplayAuditRepository(dataSource);
      UUID alphaTaskId = UUID.randomUUID();
      UUID betaTaskId = UUID.randomUUID();

      repository.append(
          new DeadLetterReplayAuditEntry(
              null,
              TENANT_ALPHA,
              alphaTaskId,
              true,
              "replay_accepted",
              Instant.parse("2026-03-12T07:00:00Z")));
      repository.append(
          new DeadLetterReplayAuditEntry(
              null,
              TENANT_BETA,
              betaTaskId,
              true,
              "replay_accepted",
              Instant.parse("2026-03-12T07:30:00Z")));

      assertThat(
              repository.countAcceptedForTaskOnDate(
                  TENANT_ALPHA, alphaTaskId, LocalDate.of(2026, 3, 12)))
          .isEqualTo(1);
      assertThat(
              repository.countAcceptedForTaskOnDate(
                  TENANT_ALPHA, betaTaskId, LocalDate.of(2026, 3, 12)))
          .isZero();
      assertThat(repository.findLatestAcceptedForTask(TENANT_ALPHA, betaTaskId)).isEmpty();
    }
  }

  private HikariDataSource createTestDataSource() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:chimera-replay-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    HikariDataSource dataSource = new HikariDataSource(config);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS api_dead_letter_replay_audit");
      statement.execute(
          """
          CREATE TABLE api_dead_letter_replay_audit (
              event_id UUID PRIMARY KEY,
              tenant_id TEXT NOT NULL,
              task_id UUID NOT NULL,
              accepted BOOLEAN NOT NULL,
              reason TEXT NOT NULL,
              occurred_at TIMESTAMP NOT NULL
          )
          """);
    }
    return dataSource;
  }
}
