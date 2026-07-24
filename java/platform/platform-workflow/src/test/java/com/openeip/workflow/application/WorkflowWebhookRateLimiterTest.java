package com.openeip.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WorkflowWebhookRateLimiterTest {
  @Test
  void boundsRequestsAndKeys() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);
    WorkflowWebhookRateLimiter limiter = new WorkflowWebhookRateLimiter(1, 60, 1, clock);

    assertThat(limiter.acquire("first")).isTrue();
    assertThat(limiter.acquire("first")).isFalse();
    assertThat(limiter.acquire("second")).isFalse();
    assertThatThrownBy(() -> new WorkflowWebhookRateLimiter(0, 60, 1, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
