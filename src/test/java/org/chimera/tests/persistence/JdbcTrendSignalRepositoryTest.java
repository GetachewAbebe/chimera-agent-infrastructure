package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import org.chimera.model.TrendSignal;
import org.chimera.persistence.JdbcTrendSignalRepository;
import org.junit.jupiter.api.Test;

class JdbcTrendSignalRepositoryTest {

  @Test
  void shouldPersistAndQueryTopTrendSignals() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcTrendSignalRepository repository = new JdbcTrendSignalRepository(dataSource);

      repository.append(
          "tenant-alpha",
          new TrendSignal(
              "streetwear-collab",
              0.91,
              "news://ethiopia/fashion/trends",
              Instant.parse("2026-03-12T08:00:00Z")));
      repository.append(
          "tenant-alpha",
          new TrendSignal(
              "creator-challenge",
              0.86,
              "twitter://mentions/recent",
              Instant.parse("2026-03-12T08:10:00Z")));
      repository.append(
          "tenant-beta",
          new TrendSignal(
              "beta-topic", 0.95, "news://beta", Instant.parse("2026-03-12T09:00:00Z")));
      repository.append(
          "tenant-alpha",
          new TrendSignal("yesterday", 0.99, "news://past", Instant.parse("2026-03-11T09:00:00Z")));

      assertThat(repository.countForTenantOnDate("tenant-alpha", LocalDate.of(2026, 3, 12)))
          .isEqualTo(2);
      assertThat(repository.topSignalsForTenantOnDate("tenant-alpha", LocalDate.of(2026, 3, 12), 2))
          .extracting(TrendSignal::topic)
          .containsExactly("streetwear-collab", "creator-challenge");
    }
  }

  private HikariDataSource createTestDataSource() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:chimera_trend_signals;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    HikariDataSource dataSource = new HikariDataSource(config);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS api_trend_signals");
      statement.execute(
          """
          CREATE TABLE api_trend_signals (
              signal_id UUID PRIMARY KEY,
              tenant_id TEXT NOT NULL,
              topic TEXT NOT NULL,
              score DOUBLE PRECISION NOT NULL,
              source TEXT NOT NULL,
              observed_at TIMESTAMP NOT NULL
          )
          """);
    }

    return dataSource;
  }
}
