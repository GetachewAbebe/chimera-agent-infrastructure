package org.chimera.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.chimera.model.DeadLetterReplayAuditEntry;

public final class JdbcDeadLetterReplayAuditRepository implements DeadLetterReplayAuditRepository {
  private final DataSource dataSource;

  public JdbcDeadLetterReplayAuditRepository(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource is required");
    }
    this.dataSource = dataSource;
  }

  @Override
  public void append(DeadLetterReplayAuditEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is required");
    }

    String sql =
        "INSERT INTO api_dead_letter_replay_audit "
            + "(event_id, tenant_id, task_id, accepted, reason, occurred_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, entry.eventId());
      statement.setString(2, entry.tenantId());
      statement.setObject(3, entry.taskId());
      statement.setBoolean(4, entry.accepted());
      statement.setString(5, entry.reason());
      statement.setTimestamp(6, Timestamp.from(entry.occurredAt()));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to append dead-letter replay audit entry", ex);
    }
  }

  @Override
  public int countAcceptedForTaskOnDate(String tenantId, UUID taskId, LocalDate date) {
    validateTaskScope(tenantId, taskId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }

    String sql =
        "SELECT COUNT(*) FROM api_dead_letter_replay_audit "
            + "WHERE tenant_id = ? AND task_id = ? AND accepted = TRUE "
            + "AND occurred_at >= ? AND occurred_at < ?";
    DateRange dateRange = utcDateRange(date);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setObject(2, taskId);
      statement.setTimestamp(3, Timestamp.from(dateRange.startInclusive()));
      statement.setTimestamp(4, Timestamp.from(dateRange.endExclusive()));
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return 0;
        }
        return resultSet.getInt(1);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to count replay audit entries", ex);
    }
  }

  @Override
  public Optional<DeadLetterReplayAuditEntry> findLatestAcceptedForTask(
      String tenantId, UUID taskId) {
    validateTaskScope(tenantId, taskId);

    String sql =
        "SELECT event_id, tenant_id, task_id, accepted, reason, occurred_at "
            + "FROM api_dead_letter_replay_audit "
            + "WHERE tenant_id = ? AND task_id = ? AND accepted = TRUE "
            + "ORDER BY occurred_at DESC LIMIT 1";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setObject(2, taskId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(map(resultSet));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to query latest replay audit entry", ex);
    }
  }

  private static DeadLetterReplayAuditEntry map(ResultSet resultSet) throws SQLException {
    return new DeadLetterReplayAuditEntry(
        UUID.fromString(resultSet.getString("event_id")),
        resultSet.getString("tenant_id"),
        UUID.fromString(resultSet.getString("task_id")),
        resultSet.getBoolean("accepted"),
        resultSet.getString("reason"),
        resultSet.getTimestamp("occurred_at").toInstant());
  }

  private static DateRange utcDateRange(LocalDate date) {
    Instant startInclusive = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new DateRange(startInclusive, endExclusive);
  }

  private static void validateTaskScope(String tenantId, UUID taskId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (taskId == null) {
      throw new IllegalArgumentException("taskId is required");
    }
  }

  private record DateRange(Instant startInclusive, Instant endExclusive) {}
}
