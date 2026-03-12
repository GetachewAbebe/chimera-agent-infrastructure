package org.chimera.infrastructure.queue;

import java.util.Optional;
import java.util.UUID;
import redis.clients.jedis.JedisPooled;

public final class RedisUuidQueuePort implements QueuePort<UUID>, AutoCloseable {
  private final JedisPooled jedis;
  private final String queueKey;

  public RedisUuidQueuePort(String redisUrl, String queueKey) {
    if (redisUrl == null || redisUrl.isBlank()) {
      throw new IllegalArgumentException("redisUrl must not be blank");
    }
    if (queueKey == null || queueKey.isBlank()) {
      throw new IllegalArgumentException("queueKey must not be blank");
    }
    this.jedis = new JedisPooled(redisUrl);
    this.queueKey = queueKey;
  }

  @Override
  public void push(UUID payload) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    try {
      jedis.rpush(queueKey, payload.toString());
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to push UUID payload to Redis queue", ex);
    }
  }

  @Override
  public Optional<UUID> poll() {
    try {
      String payload = jedis.lpop(queueKey);
      if (payload == null || payload.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(UUID.fromString(payload));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to poll UUID payload from Redis queue", ex);
    }
  }

  @Override
  public int size() {
    try {
      long queueSize = jedis.llen(queueKey);
      return queueSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) queueSize;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to read Redis queue size", ex);
    }
  }

  @Override
  public void close() {
    jedis.close();
  }
}
