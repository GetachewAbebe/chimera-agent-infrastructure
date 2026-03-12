package org.chimera.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.chimera.api.RateLimiter;

public final class JdbcRequestRateLimiter implements RateLimiter {
  private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

  private final DataSource dataSource;
  private final int maxRequestsPerWindow;
  private final long windowMillis;

  public JdbcRequestRateLimiter(DataSource dataSource, int maxRequestsPerWindow, Duration window) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource is required");
    }
    if (maxRequestsPerWindow < 1) {
      throw new IllegalArgumentException("maxRequestsPerWindow must be >= 1");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    this.dataSource = dataSource;
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowMillis = window.toMillis();
  }

  @Override
  public boolean allow(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("rate limit key is required");
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try (Connection connection = dataSource.getConnection()) {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
          long now = System.currentTimeMillis();
          WindowRecord windowRecord = lockWindow(connection, key);
          if (windowRecord == null) {
            try {
              insertWindow(connection, key, now);
              connection.commit();
              return true;
            } catch (SQLException ex) {
              connection.rollback();
              if (isDuplicateKeyViolation(ex) && attempt == 0) {
                continue;
              }
              throw ex;
            }
          }

          if (now - windowRecord.windowStartMillis >= windowMillis) {
            resetWindow(connection, key, now);
            connection.commit();
            return true;
          }

          if (windowRecord.requestCount < maxRequestsPerWindow) {
            incrementWindow(connection, key, windowRecord.requestCount + 1);
            connection.commit();
            return true;
          }

          connection.commit();
          return false;
        } catch (SQLException ex) {
          connection.rollback();
          throw new IllegalStateException("Failed to evaluate JDBC rate limit", ex);
        } finally {
          connection.setAutoCommit(originalAutoCommit);
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Failed to connect for JDBC rate limit", ex);
      }
    }

    throw new IllegalStateException("Failed to apply JDBC rate limit due to write conflicts");
  }

  private static WindowRecord lockWindow(Connection connection, String key) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT window_start_ms, request_count FROM api_rate_limits WHERE limiter_key = ? FOR UPDATE")) {
      statement.setString(1, key);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return new WindowRecord(
            resultSet.getLong("window_start_ms"), resultSet.getInt("request_count"));
      }
    }
  }

  private static void insertWindow(Connection connection, String key, long now)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO api_rate_limits (limiter_key, window_start_ms, request_count, updated_at) "
                + "VALUES (?, ?, 1, CURRENT_TIMESTAMP)")) {
      statement.setString(1, key);
      statement.setLong(2, now);
      statement.executeUpdate();
    }
  }

  private static void resetWindow(Connection connection, String key, long now) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE api_rate_limits SET window_start_ms = ?, request_count = 1, updated_at = CURRENT_TIMESTAMP "
                + "WHERE limiter_key = ?")) {
      statement.setLong(1, now);
      statement.setString(2, key);
      statement.executeUpdate();
    }
  }

  private static void incrementWindow(Connection connection, String key, int nextCount)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE api_rate_limits SET request_count = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE limiter_key = ?")) {
      statement.setInt(1, nextCount);
      statement.setString(2, key);
      statement.executeUpdate();
    }
  }

  private static boolean isDuplicateKeyViolation(SQLException ex) {
    return SQLSTATE_UNIQUE_VIOLATION.equals(ex.getSQLState());
  }

  private record WindowRecord(long windowStartMillis, int requestCount) {}
}
