package com.openeip.workflow.application;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Invalid limit configuration must abort construction.")
public class WorkflowWebhookRateLimiter {
  private final int requestLimit;
  private final long windowMillis;
  private final int maxKeys;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Autowired
  public WorkflowWebhookRateLimiter(
      @Value("${openeip.workflow.webhook-rate-limit.requests:60}") int requestLimit,
      @Value("${openeip.workflow.webhook-rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${openeip.workflow.webhook-rate-limit.max-keys:10000}") int maxKeys) {
    this(requestLimit, windowSeconds, maxKeys, Clock.systemUTC());
  }

  WorkflowWebhookRateLimiter(int requestLimit, long windowSeconds, int maxKeys, Clock clock) {
    if (requestLimit < 1 || windowSeconds < 1 || maxKeys < 1) {
      throw new IllegalArgumentException("Webhook rate-limit configuration must be positive");
    }
    this.requestLimit = requestLimit;
    this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
    this.maxKeys = maxKeys;
    this.clock = clock;
  }

  public synchronized boolean acquire(String hookId) {
    long now = clock.millis();
    Window current = windows.get(hookId);
    if (current == null || current.expiresAtMillis() <= now) {
      if (current == null && windows.size() >= maxKeys) {
        removeExpired(now);
      }
      if (current == null && windows.size() >= maxKeys) {
        return false;
      }
      windows.put(hookId, new Window(1, now + windowMillis));
      return true;
    }
    if (current.count() >= requestLimit) {
      return false;
    }
    windows.put(hookId, new Window(current.count() + 1, current.expiresAtMillis()));
    return true;
  }

  private void removeExpired(long now) {
    Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue().expiresAtMillis() <= now) {
        iterator.remove();
      }
    }
  }

  private record Window(int count, long expiresAtMillis) {}
}
