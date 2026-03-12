package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.chimera.model.WalletLedgerEntry;
import org.chimera.persistence.JdbcWalletLedgerRepository;
import org.junit.jupiter.api.Test;

class JdbcWalletLedgerRepositoryTest {

  @Test
  void shouldPersistAndSummarizeDailySpend() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcWalletLedgerRepository repository = new JdbcWalletLedgerRepository(dataSource);
      LocalDate today = LocalDate.now(ZoneOffset.UTC);

      repository.append(
          new WalletLedgerEntry(
              null,
              "tenant-alpha",
              UUID.randomUUID(),
              "worker-alpha",
              new BigDecimal("20.00"),
              "simulated",
              "sim-tx-1",
              today.atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)));
      repository.append(
          new WalletLedgerEntry(
              null,
              "tenant-alpha",
              UUID.randomUUID(),
              "worker-alpha",
              new BigDecimal("7.50"),
              "simulated",
              "sim-tx-2",
              Instant.now()));
      repository.append(
          new WalletLedgerEntry(
              null,
              "tenant-alpha",
              UUID.randomUUID(),
              "worker-alpha",
              new BigDecimal("5.00"),
              "simulated",
              "sim-tx-prev",
              today.minusDays(1).atStartOfDay().plusHours(3).toInstant(ZoneOffset.UTC)));

      assertThat(repository.sumForTenantOnDate("tenant-alpha", today))
          .isEqualByComparingTo("27.50");
      assertThat(repository.countForTenantOnDate("tenant-alpha", today)).isEqualTo(2);
      assertThat(repository.sumForTenantOnDate("tenant-alpha", today.minusDays(1)))
          .isEqualByComparingTo("5.00");
    }
  }

  @Test
  void shouldEnforceTenantScopedAggregation() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcWalletLedgerRepository repository = new JdbcWalletLedgerRepository(dataSource);
      LocalDate today = LocalDate.now(ZoneOffset.UTC);

      repository.append(
          new WalletLedgerEntry(
              null,
              "tenant-alpha",
              UUID.randomUUID(),
              "worker-alpha",
              new BigDecimal("9.00"),
              "simulated",
              "sim-tx-1",
              Instant.now()));
      repository.append(
          new WalletLedgerEntry(
              null,
              "tenant-beta",
              UUID.randomUUID(),
              "worker-beta",
              new BigDecimal("11.00"),
              "simulated",
              "sim-tx-2",
              Instant.now()));

      assertThat(repository.sumForTenantOnDate("tenant-alpha", today)).isEqualByComparingTo("9.00");
      assertThat(repository.countForTenantOnDate("tenant-alpha", today)).isEqualTo(1);
      assertThat(repository.sumForTenantOnDate("tenant-beta", today)).isEqualByComparingTo("11.00");
      assertThat(repository.countForTenantOnDate("tenant-beta", today)).isEqualTo(1);
    }
  }

  private HikariDataSource createTestDataSource() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:chimera_wallet_ledger;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    HikariDataSource dataSource = new HikariDataSource(config);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS api_wallet_ledger");
      statement.execute(
          """
          CREATE TABLE api_wallet_ledger (
              entry_id UUID PRIMARY KEY,
              tenant_id TEXT NOT NULL,
              task_id UUID NOT NULL,
              worker_id TEXT NOT NULL,
              provider TEXT NOT NULL,
              transaction_id TEXT NOT NULL,
              amount_usd NUMERIC(18, 2) NOT NULL,
              executed_at TIMESTAMP NOT NULL
          )
          """);
    }

    return dataSource;
  }
}
