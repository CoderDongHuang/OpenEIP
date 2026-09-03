package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Request to authorize or re-authorize an execution reservation. */
public record BudgetDecisionRequest(
    UUID tenantId,
    UUID budgetId,
    UUID executionId,
    BudgetDecisionType type,
    BigDecimal reservedAmount,
    Instant now) {

  public BudgetDecisionRequest {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(budgetId, "budgetId is required");
    Objects.requireNonNull(executionId, "executionId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(reservedAmount, "reservedAmount is required");
    Objects.requireNonNull(now, "now is required");
    if (reservedAmount.signum() < 0
        || reservedAmount.scale() > 6
        || reservedAmount.precision() - reservedAmount.scale() > 14) {
      throw new IllegalArgumentException(
          "reservedAmount must fit DECIMAL(20,6) and be non-negative");
    }
  }
}
