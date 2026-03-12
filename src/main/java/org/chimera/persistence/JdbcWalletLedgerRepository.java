package org.chimera.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.chimera.model.WalletLedgerEntry;

public final class JdbcWalletLedgerRepository implements WalletLedgerRepository {
  private final DataSource dataSource;

  public JdbcWalletLedgerRepository(DataSource dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource is required");
    }
    this.dataSource = dataSource;
  }

  @Override
  public void append(WalletLedgerEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("entry is required");
    }

    String sql =
        "INSERT INTO api_wallet_ledger "
            + "(entry_id, tenant_id, task_id, worker_id, provider, transaction_id, amount_usd, executed_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, entry.ledgerId());
      statement.setString(2, entry.tenantId());
      statement.setObject(3, entry.taskId());
      statement.setString(4, entry.workerId());
      statement.setString(5, entry.provider());
      statement.setString(6, entry.transactionId());
      statement.setBigDecimal(7, entry.amountUsd());
      statement.setTimestamp(8, Timestamp.from(entry.executedAt()));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to append wallet ledger entry", ex);
    }
  }

  @Override
  public BigDecimal sumForTenantOnDate(String tenantId, LocalDate date) {
    validateTenantAndDate(tenantId, date);

    String sql =
        "SELECT COALESCE(SUM(amount_usd), 0) "
            + "FROM api_wallet_ledger WHERE tenant_id = ? AND executed_at >= ? AND executed_at < ?";
    DateRange dateRange = utcDateRange(date);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tenantId);
      statement.setTimestamp(2, Timestamp.from(dateRange.startInclusive()));
      statement.setTimestamp(3, Timestamp.from(dateRange.endExclusive()));

      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return BigDecimal.ZERO;
        }
        BigDecimal sum = resultSet.getBigDecimal(1);
        return sum == null ? BigDecimal.ZERO : sum;
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Failed to summarize wallet ledger spend", ex);
    }
  }

  @Override
  public int countForTenantOnDate(String tenantId, LocalDate date) {
    validateTenantAndDate(tenantId, date);

    String sql =
        "SELECT COUNT(*) "
            + "FROM api_wallet_ledger WHERE tenant_id = ? AND executed_at >= ? AND executed_at < ?";
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
      throw new IllegalStateException("Failed to count wallet ledger transfers", ex);
    }
  }

  private static DateRange utcDateRange(LocalDate date) {
    Instant startInclusive = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant endExclusive = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new DateRange(startInclusive, endExclusive);
  }

  private static void validateTenantAndDate(String tenantId, LocalDate date) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    if (date == null) {
      throw new IllegalArgumentException("date is required");
    }
  }

  private record DateRange(Instant startInclusive, Instant endExclusive) {}
}
