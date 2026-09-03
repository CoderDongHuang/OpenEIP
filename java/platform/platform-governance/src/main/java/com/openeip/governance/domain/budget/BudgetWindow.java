package com.openeip.governance.domain.budget;

import java.time.Instant;

/** Half-open UTC interval used for budget aggregation. */
public record BudgetWindow(BudgetWindowType type, Instant start, Instant end) {
  public BudgetWindow {
    if (type == null || start == null || end == null || !start.isBefore(end)) {
      throw new IllegalArgumentException("budget window must be a non-empty interval");
    }
  }
}
