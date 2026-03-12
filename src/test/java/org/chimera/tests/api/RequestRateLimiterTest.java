package org.chimera.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.chimera.api.RequestRateLimiter;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

  @Test
  void shouldAllowRequestsUpToLimitThenBlock() {
    RequestRateLimiter limiter = new RequestRateLimiter(2, Duration.ofMinutes(1));

    assertThat(limiter.allow("tenant-alpha:/api/campaigns")).isTrue();
    assertThat(limiter.allow("tenant-alpha:/api/campaigns")).isTrue();
    assertThat(limiter.allow("tenant-alpha:/api/campaigns")).isFalse();
  }

  @Test
  void shouldResetCounterAfterWindowExpires() throws InterruptedException {
    RequestRateLimiter limiter = new RequestRateLimiter(1, Duration.ofMillis(25));
    String key = "tenant-alpha:/api/review/task-1/approve";

    assertThat(limiter.allow(key)).isTrue();
    assertThat(limiter.allow(key)).isFalse();

    Thread.sleep(40);

    assertThat(limiter.allow(key)).isTrue();
  }
}
