package com.openeip.governance.domain.quota;

import java.time.Instant;

/** Half-open UTC interval used to aggregate one quota policy. */
public record QuotaWindow(QuotaWindowType type, Instant start, Instant end) {
  public QuotaWindow {
    if (type == null || start == null || end == null || !start.isBefore(end)) {
      throw new IllegalArgumentException("quota window must be a non-empty interval");
    }
  }
}
