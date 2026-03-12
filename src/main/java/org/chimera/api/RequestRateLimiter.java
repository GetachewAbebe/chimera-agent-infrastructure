package org.chimera.api;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RequestRateLimiter implements RateLimiter {
  private final int maxRequestsPerWindow;
  private final long windowMillis;
  private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public RequestRateLimiter(int maxRequestsPerWindow, Duration window) {
    if (maxRequestsPerWindow < 1) {
      throw new IllegalArgumentException("maxRequestsPerWindow must be >= 1");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.windowMillis = window.toMillis();
  }

  @Override
  public boolean allow(String key) {
    long now = System.currentTimeMillis();
    WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));

    synchronized (counter) {
      if (now - counter.windowStartMillis >= windowMillis) {
        counter.windowStartMillis = now;
        counter.count.set(0);
      }

      int current = counter.count.incrementAndGet();
      return current <= maxRequestsPerWindow;
    }
  }

  private static final class WindowCounter {
    private long windowStartMillis;
    private final AtomicInteger count = new AtomicInteger(0);

    private WindowCounter(long windowStartMillis) {
      this.windowStartMillis = windowStartMillis;
    }
  }
}
