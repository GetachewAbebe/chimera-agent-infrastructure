package org.chimera.api;

public interface RateLimiter {
  boolean allow(String key);
}
