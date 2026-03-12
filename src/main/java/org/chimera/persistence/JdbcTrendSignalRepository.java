package org.chimera.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.chimera.model.TrendSignal;

public final class JdbcTrendSignalRepository implements TrendSignalRepository {
  private final DataSource dataSource;

  public JdbcTrendSignalRepository(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource is required");
    }
    this.dataSource = dataSource;
  }

  @Override
  public void append(String tenantId, TrendSignal signal) {
    validateTenant(tenantId);
    if (signal == null) {
      throw new IllegalArgumentException("signal is required");
    }
    if (signal.topic() == null || signal.topic().isBlank()) {
      throw new IllegalArgumentException("signal.topic must not be blank");
    }
    if (signal.source() == null || signal.source().isBlank()) {
      throw new IllegalArgumentException("signal.source must not be blank");
    }
    if (signal.observedAt() == null) {
      throw new IllegalArgumentException("signal.observedAt is required");
    }

    String sql =
        "INSERT INTO api_trend_signals "
            + "(signal_id, tenant_id, topic, score, source, observed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, UUID.randomUUID());
      statement.setString(2, tenantId);
      statement.setString(3, signal.topic());
      statement.setDouble(4, signal.score());
      statement.setString(5, signal.source());
      statement.setTimestamp(6, Timestamp.from(signal.observedAt()));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to append trend signal", ex);
    }
  }

  @Override
  public int countForTenantOnDate(String tenantId, LocalDate date) {
    validateTenantAndDate(tenantId, date);

    String sql =
        "SELECT COUNT(*) FROM api_trend_signals "
            + "WHERE tenant_id = ? AND observed_at >= ? AND observed_at < ?";

    DateRange dateRange = utcDateRange(date);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setTimestamp(2, Timestamp.from(dateRange.startInclusive()));
      statement.setTimestamp(3, Timestamp.from(dateRange.endExclusive()));
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return 0;
        }
        return resultSet.getInt(1);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to count trend signals", ex);
    }
  }

  @Override
  public List<TrendSignal> topSignalsForTenantOnDate(String tenantId, LocalDate date, int limit) {
    validateTenantAndDate(tenantId, date);
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }

    String sql =
        "SELECT topic, score, source, observed_at FROM api_trend_signals "
            + "WHERE tenant_id = ? AND observed_at >= ? AND observed_at < ? "
            + "ORDER BY score DESC, observed_at DESC LIMIT ?";

    DateRange dateRange = utcDateRange(date);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setTimestamp(2, Timestamp.from(dateRange.startInclusive()));
      statement.setTimestamp(3, Timestamp.from(dateRange.endExclusive()));
      statement.setInt(4, limit);

      try (ResultSet resultSet = statement.executeQuery()) {
        List<TrendSignal> signals = new ArrayList<>();
        while (resultSet.next()) {
          signals.add(
              new TrendSignal(
                  resultSet.getString("topic"),
                  resultSet.getDouble("score"),
                  resultSet.getString("source"),
                  resultSet.getTimestamp("observed_at").toInstant()));
        }
        return signals;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to query top trend signals", ex);
    }
  }

  private static void validateTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
  }

  private static void validateTenantAndDate(String tenantId, LocalDate date) {
    validateTenant(tenantId);
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
  }

  private static DateRange utcDateRange(LocalDate date) {
    Instant startInclusive = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new DateRange(startInclusive, endExclusive);
  }

  private record DateRange(Instant startInclusive, Instant endExclusive) {}
}
