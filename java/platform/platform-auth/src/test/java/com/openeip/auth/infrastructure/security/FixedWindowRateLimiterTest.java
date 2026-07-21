package com.openeip.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

  @Test
  void limitsPerKeyAndResetsAfterWindow() {
    MutableClock clock = new MutableClock();
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, 10, 10, clock);

    assertThat(limiter.acquire("one").allowed()).isTrue();
    assertThat(limiter.acquire("one").allowed()).isTrue();
    FixedWindowRateLimiter.Decision denied = limiter.acquire("one");
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(10);
    assertThat(limiter.acquire("two").allowed()).isTrue();

    clock.advanceSeconds(10);
    assertThat(limiter.acquire("one").allowed()).isTrue();
  }

  @Test
  void rejectsNewKeysWhenBoundIsFullAndRecoversAfterExpiry() {
    MutableClock clock = new MutableClock();
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 10, 1, clock);

    assertThat(limiter.acquire("one").allowed()).isTrue();
    assertThat(limiter.acquire("two").allowed()).isFalse();
    clock.advanceSeconds(10);
    assertThat(limiter.acquire("two").allowed()).isTrue();
  }

  @Test
  void rejectsInvalidConfiguration() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new FixedWindowRateLimiter(0, 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-07-22T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advanceSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }
}
