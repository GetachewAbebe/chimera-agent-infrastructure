package org.chimera.persistence;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.chimera.api.RateLimiter;
import redis.clients.jedis.JedisPooled;

public final class RedisRequestRateLimiter implements RateLimiter, AutoCloseable {
  private static final String LUA_TOKEN_BUCKET =
      """
      local tokens_key = KEYS[1]
      local timestamp_key = KEYS[2]
      local capacity = tonumber(ARGV[1])
      local refill_per_ms = tonumber(ARGV[2])
      local now_ms = tonumber(ARGV[3])
      local requested = tonumber(ARGV[4])
      local ttl_ms = tonumber(ARGV[5])

      local tokens = tonumber(redis.call('GET', tokens_key))
      if tokens == nil then
        tokens = capacity
      end

      local last_refill = tonumber(redis.call('GET', timestamp_key))
      if last_refill == nil then
        last_refill = now_ms
      end

      local elapsed = math.max(0, now_ms - last_refill)
      local replenished = math.min(capacity, tokens + (elapsed * refill_per_ms))
      local allowed = replenished >= requested
      local remaining = replenished
      if allowed then
        remaining = replenished - requested
      end

      redis.call('SET', tokens_key, remaining, 'PX', ttl_ms)
      redis.call('SET', timestamp_key, now_ms, 'PX', ttl_ms)

      if allowed then
        return 1
      end
      return 0
      """;

  private final ScriptRunner scriptRunner;
  private final int capacity;
  private final long windowMillis;
  private final double refillPerMillis;
  private final long ttlMillis;

  public RedisRequestRateLimiter(String redisUrl, int maxRequestsPerWindow, Duration window) {
    this(new JedisScriptRunner(redisUrl), maxRequestsPerWindow, window);
  }

  RedisRequestRateLimiter(ScriptRunner scriptRunner, int maxRequestsPerWindow, Duration window) {
    if (scriptRunner == null) {
      throw new IllegalArgumentException("scriptRunner is required");
    }
    if (maxRequestsPerWindow < 1) {
      throw new IllegalArgumentException("maxRequestsPerWindow must be >= 1");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    this.scriptRunner = scriptRunner;
    this.capacity = maxRequestsPerWindow;
    this.windowMillis = window.toMillis();
    this.refillPerMillis = (double) maxRequestsPerWindow / (double) this.windowMillis;
    this.ttlMillis = this.windowMillis * 2;
  }

  public boolean ping() {
    return scriptRunner.ping();
  }

  @Override
  public boolean allow(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("rate limit key is required");
    }

    long now = System.currentTimeMillis();
    String bucketKey = "chimera:ratelimit:tokens:" + key;
    String timestampKey = "chimera:ratelimit:ts:" + key;
    Object result =
        scriptRunner.eval(
            LUA_TOKEN_BUCKET,
            List.of(bucketKey, timestampKey),
            List.of(
                Integer.toString(capacity),
                Double.toString(refillPerMillis),
                Long.toString(now),
                "1",
                Long.toString(ttlMillis)));
    return parseAllowed(result);
  }

  @Override
  public void close() {
    scriptRunner.close();
  }

  private static boolean parseAllowed(Object result) {
    if (result instanceof Number number) {
      long value = number.longValue();
      if (value == 1L) {
        return true;
      }
      if (value == 0L) {
        return false;
      }
      throw new IllegalStateException("Unexpected Redis rate-limit script result: " + result);
    }
    if (result instanceof String text) {
      String normalized = text.trim();
      if ("1".equals(normalized)) {
        return true;
      }
      if ("0".equals(normalized)) {
        return false;
      }
      throw new IllegalStateException("Unexpected Redis rate-limit script result: " + result);
    }
    throw new IllegalStateException("Unexpected Redis rate-limit script result: " + result);
  }

  interface ScriptRunner extends AutoCloseable {
    Object eval(String script, List<String> keys, List<String> args);

    boolean ping();

    @Override
    void close();
  }

  static final class JedisScriptRunner implements ScriptRunner {
    private final JedisPooled jedis;

    JedisScriptRunner(String redisUrl) {
      if (redisUrl == null || redisUrl.isBlank()) {
        throw new IllegalArgumentException("redisUrl is required");
      }
      this.jedis = new JedisPooled(URI.create(redisUrl));
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
      return jedis.eval(script, keys, args);
    }

    @Override
    public boolean ping() {
      return "PONG".equalsIgnoreCase(jedis.ping());
    }

    @Override
    public void close() {
      jedis.close();
    }
  }
}
