package org.chimera.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.chimera.model.Task;
import redis.clients.jedis.JedisPooled;

public final class RedisTaskQueuePort implements QueuePort<Task>, AutoCloseable {
  private final JedisPooled jedis;
  private final ObjectMapper objectMapper;
  private final String queueKey;

  public RedisTaskQueuePort(String redisUrl, String queueKey, ObjectMapper objectMapper) {
    if (redisUrl == null || redisUrl.isBlank()) {
      throw new IllegalArgumentException("redisUrl must not be blank");
    }
    if (queueKey == null || queueKey.isBlank()) {
      throw new IllegalArgumentException("queueKey must not be blank");
    }
    if (objectMapper == null) {
      throw new IllegalArgumentException("objectMapper is required");
    }
    this.jedis = new JedisPooled(redisUrl);
    this.queueKey = queueKey;
    this.objectMapper = objectMapper;
  }

  @Override
  public void push(Task payload) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    try {
      jedis.rpush(queueKey, objectMapper.writeValueAsString(payload));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to push task payload to Redis queue", ex);
    }
  }

  @Override
  public Optional<Task> poll() {
    try {
      String payload = jedis.lpop(queueKey);
      if (payload == null || payload.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(payload, Task.class));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to poll task payload from Redis queue", ex);
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
