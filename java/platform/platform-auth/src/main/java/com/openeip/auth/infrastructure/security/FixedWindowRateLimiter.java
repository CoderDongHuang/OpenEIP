package com.openeip.auth.infrastructure.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory fixed-window limiter for a single Auth service instance. */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Invalid limit configuration must abort construction.")
public class FixedWindowRateLimiter {

  private final int requestLimit;
  private final long windowMillis;
  private final int maxKeys;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public FixedWindowRateLimiter(int requestLimit, long windowSeconds, int maxKeys) {
    this(requestLimit, windowSeconds, maxKeys, Clock.systemUTC());
  }

  FixedWindowRateLimiter(int requestLimit, long windowSeconds, int maxKeys, Clock clock) {
    if (requestLimit < 1 || windowSeconds < 1 || maxKeys < 1) {
      throw new IllegalArgumentException("Rate-limit configuration must be positive");
    }
    this.requestLimit = requestLimit;
    this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
    this.maxKeys = maxKeys;
    this.clock = clock;
  }

  public synchronized Decision acquire(String key) {
    long now = clock.millis();
    Window current = windows.get(key);
    if (current == null || current.expiresAtMillis <= now) {
      if (current == null && windows.size() >= maxKeys) {
        removeExpired(now);
      }
      if (current == null && windows.size() >= maxKeys) {
        return new Decision(false, Math.max(1, windowMillis / 1000));
      }
      windows.put(key, new Window(1, now + windowMillis));
      return new Decision(true, 0);
    }
    if (current.count >= requestLimit) {
      return new Decision(false, Math.max(1, (current.expiresAtMillis - now + 999) / 1000));
    }
    windows.put(key, new Window(current.count + 1, current.expiresAtMillis));
    return new Decision(true, 0);
  }

  private void removeExpired(long now) {
    Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue().expiresAtMillis <= now) {
        iterator.remove();
      }
    }
  }

  private record Window(int count, long expiresAtMillis) {}

  public record Decision(boolean allowed, long retryAfterSeconds) {}
}
