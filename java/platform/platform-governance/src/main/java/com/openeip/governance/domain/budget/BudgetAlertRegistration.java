package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Validated input for an idempotent threshold alert. */
public record BudgetAlertRegistration(
    UUID tenantId,
    UUID budgetId,
    Instant windowStart,
    BigDecimal threshold,
    long crossingRevision,
    Instant now) {

  public BudgetAlertRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(budgetId, "budgetId is required");
    Objects.requireNonNull(windowStart, "windowStart is required");
    Objects.requireNonNull(threshold, "threshold is required");
    Objects.requireNonNull(now, "now is required");
    if (threshold.signum() <= 0
        || threshold.compareTo(BigDecimal.ONE) > 0
        || threshold.scale() > 5
        || threshold.precision() - threshold.scale() > 3) {
      throw new IllegalArgumentException("threshold must be in (0,1] and fit DECIMAL(8,5)");
    }
    if (crossingRevision < 0) {
      throw new IllegalArgumentException("crossingRevision must be non-negative");
    }
  }
}
