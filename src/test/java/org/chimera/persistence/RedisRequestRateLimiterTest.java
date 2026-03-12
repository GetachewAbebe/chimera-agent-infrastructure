package org.chimera.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisRequestRateLimiterTest {

  @Test
  void shouldAllowWhenScriptReturnsOne() {
    FakeScriptRunner runner = new FakeScriptRunner(true, 1L);
    RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(runner, 2, Duration.ofMinutes(1));

    assertThat(limiter.allow("tenant-alpha:/api/campaigns")).isTrue();
    assertThat(runner.evalCalls).isEqualTo(1);
  }

  @Test
  void shouldRejectWhenScriptReturnsZero() {
    FakeScriptRunner runner = new FakeScriptRunner(true, 0L);
    RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(runner, 2, Duration.ofMinutes(1));

    assertThat(limiter.allow("tenant-alpha:/api/campaigns")).isFalse();
  }

  @Test
  void shouldThrowOnUnexpectedScriptResult() {
    FakeScriptRunner runner = new FakeScriptRunner(true, "unexpected");
    RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(runner, 2, Duration.ofMinutes(1));

    assertThatThrownBy(() -> limiter.allow("tenant-alpha:/api/campaigns"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unexpected Redis rate-limit script result");
  }

  @Test
  void shouldExposePingAndCloseRunner() {
    FakeScriptRunner runner = new FakeScriptRunner(true, 1L);
    RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(runner, 2, Duration.ofMinutes(1));

    assertThat(limiter.ping()).isTrue();
    limiter.close();
    assertThat(runner.closed).isTrue();
  }

  private static final class FakeScriptRunner implements RedisRequestRateLimiter.ScriptRunner {
    private final boolean ping;
    private final Deque<Object> results = new ArrayDeque<>();
    private int evalCalls = 0;
    private boolean closed = false;

    private FakeScriptRunner(boolean ping, Object... scriptResults) {
      this.ping = ping;
      results.addAll(List.of(scriptResults));
    }

    @Override
    public Object eval(String script, List<String> keys, List<String> args) {
      evalCalls += 1;
      return results.isEmpty() ? 1L : results.removeFirst();
    }

    @Override
    public boolean ping() {
      return ping;
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
