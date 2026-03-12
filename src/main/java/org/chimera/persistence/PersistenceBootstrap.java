package org.chimera.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.chimera.api.RateLimiter;
import org.chimera.api.RequestRateLimiter;
import org.flywaydb.core.Flyway;

public final class PersistenceBootstrap {
  private static final String DEFAULT_DB_URL = "";
  private static final String DEFAULT_REDIS_URL = "";
  private static final String DEFAULT_DB_USER = "chimera";
  private static final String DEFAULT_DB_PASSWORD = "chimera";
  private static final int DEFAULT_WRITE_RATE_LIMIT_MAX_REQUESTS = 30;
  private static final int DEFAULT_WRITE_RATE_LIMIT_WINDOW_SECONDS = 60;
  private static final String ENV_REDIS_URL = "REDIS_URL";
  private static final String ENV_WRITE_RATE_LIMIT_MAX_REQUESTS =
      "CHIMERA_WRITE_RATE_LIMIT_MAX_REQUESTS";
  private static final String ENV_WRITE_RATE_LIMIT_WINDOW_SECONDS =
      "CHIMERA_WRITE_RATE_LIMIT_WINDOW_SECONDS";

  private PersistenceBootstrap() {}

  public static PersistenceBundle initialize() {
    String dbUrl = System.getenv().getOrDefault("POSTGRES_URL", DEFAULT_DB_URL);
    String redisUrl = System.getenv().getOrDefault(ENV_REDIS_URL, DEFAULT_REDIS_URL);
    int maxRequests =
        parsePositiveInt(
            System.getenv(ENV_WRITE_RATE_LIMIT_MAX_REQUESTS),
            DEFAULT_WRITE_RATE_LIMIT_MAX_REQUESTS);
    int windowSeconds =
        parsePositiveInt(
            System.getenv(ENV_WRITE_RATE_LIMIT_WINDOW_SECONDS),
            DEFAULT_WRITE_RATE_LIMIT_WINDOW_SECONDS);
    Duration window = Duration.ofSeconds(windowSeconds);

    if (dbUrl.isBlank()) {
      RateLimiter limiter = buildRateLimiter(redisUrl, maxRequests, window, null);
      return new PersistenceBundle(
          new InMemoryTaskRepository(),
          new InMemoryWalletLedgerRepository(),
          new InMemoryTrendSignalRepository(),
          new InMemoryDeadLetterReplayAuditRepository(),
          limiter,
          () -> closeRateLimiter(limiter));
    }

    HikariDataSource dataSource = createDataSource(dbUrl);
    migrate(dataSource);

    TaskRepository taskRepository = new JdbcTaskRepository(dataSource, new ObjectMapper());
    WalletLedgerRepository walletLedgerRepository = new JdbcWalletLedgerRepository(dataSource);
    TrendSignalRepository trendSignalRepository = new JdbcTrendSignalRepository(dataSource);
    DeadLetterReplayAuditRepository deadLetterReplayAuditRepository =
        new JdbcDeadLetterReplayAuditRepository(dataSource);
    RateLimiter rateLimiter = buildRateLimiter(redisUrl, maxRequests, window, dataSource);
    return new PersistenceBundle(
        taskRepository,
        walletLedgerRepository,
        trendSignalRepository,
        deadLetterReplayAuditRepository,
        rateLimiter,
        () -> {
          closeRateLimiter(rateLimiter);
          dataSource.close();
        });
  }

  private static HikariDataSource createDataSource(String dbUrl) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dbUrl);
    config.setUsername(System.getenv().getOrDefault("POSTGRES_USER", DEFAULT_DB_USER));
    config.setPassword(System.getenv().getOrDefault("POSTGRES_PASSWORD", DEFAULT_DB_PASSWORD));
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(1);
    config.setPoolName("chimera-pool");
    return new HikariDataSource(config);
  }

  private static void migrate(HikariDataSource dataSource) {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
  }

  private static int parsePositiveInt(String raw, int fallback) {
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static RateLimiter buildRateLimiter(
      String redisUrl, int maxRequests, Duration window, HikariDataSource dataSourceOrNull) {
    if (redisUrl != null && !redisUrl.isBlank()) {
      try {
        RedisRequestRateLimiter redisLimiter =
            new RedisRequestRateLimiter(redisUrl, maxRequests, window);
        if (redisLimiter.ping()) {
          return redisLimiter;
        }
        redisLimiter.close();
      } catch (Exception ex) {
        System.err.println("Redis rate limiter unavailable, falling back: " + ex.getMessage());
      }
    }

    if (dataSourceOrNull != null) {
      return new JdbcRequestRateLimiter(dataSourceOrNull, maxRequests, window);
    }
    return new RequestRateLimiter(maxRequests, window);
  }

  private static void closeRateLimiter(RateLimiter limiter) {
    if (limiter instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to close rate limiter", ex);
      }
    }
  }
}
