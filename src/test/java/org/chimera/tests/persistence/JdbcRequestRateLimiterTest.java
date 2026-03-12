package org.chimera.tests.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import org.chimera.persistence.JdbcRequestRateLimiter;
import org.junit.jupiter.api.Test;

class JdbcRequestRateLimiterTest {

  @Test
  void shouldAllowRequestsUpToLimitThenBlock() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcRequestRateLimiter limiter =
          new JdbcRequestRateLimiter(dataSource, 2, Duration.ofMinutes(1));
      String key = "tenant-alpha:/api/campaigns";

      assertThat(limiter.allow(key)).isTrue();
      assertThat(limiter.allow(key)).isTrue();
      assertThat(limiter.allow(key)).isFalse();
    }
  }

  @Test
  void shouldResetAfterWindowExpires() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcRequestRateLimiter limiter =
          new JdbcRequestRateLimiter(dataSource, 1, Duration.ofMillis(25));
      String key = "tenant-alpha:/api/review/task-1/approve";

      assertThat(limiter.allow(key)).isTrue();
      assertThat(limiter.allow(key)).isFalse();
      Thread.sleep(40);
      assertThat(limiter.allow(key)).isTrue();
    }
  }

  @Test
  void shouldShareCountersAcrossLimiterInstances() throws Exception {
    try (HikariDataSource dataSource = createTestDataSource()) {
      JdbcRequestRateLimiter limiterA =
          new JdbcRequestRateLimiter(dataSource, 1, Duration.ofMinutes(1));
      JdbcRequestRateLimiter limiterB =
          new JdbcRequestRateLimiter(dataSource, 1, Duration.ofMinutes(1));
      String key = "tenant-alpha:/api/campaigns";

      assertThat(limiterA.allow(key)).isTrue();
      assertThat(limiterB.allow(key)).isFalse();
    }
  }

  private HikariDataSource createTestDataSource() throws Exception {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:chimera_rate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    config.setUsername("sa");
    config.setPassword("");

    HikariDataSource dataSource = new HikariDataSource(config);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS api_rate_limits");
      statement.execute(
          """
          CREATE TABLE api_rate_limits (
              limiter_key TEXT PRIMARY KEY,
              window_start_ms BIGINT NOT NULL,
              request_count INT NOT NULL,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
          """);
    }

    return dataSource;
  }
}
