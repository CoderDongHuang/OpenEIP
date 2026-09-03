package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence input for an append-only budget decision. */
public record BudgetDecisionRegistration(
    UUID tenantId,
    UUID budgetId,
    UUID executionId,
    BudgetDecisionType type,
    String policyVersion,
    BigDecimal observedAmount,
    BigDecimal reservedAmount,
    BudgetDecisionOutcome decision,
    Instant createdAt) {

  public BudgetDecisionRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(budgetId, "budgetId is required");
    Objects.requireNonNull(executionId, "executionId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(observedAmount, "observedAmount is required");
    Objects.requireNonNull(reservedAmount, "reservedAmount is required");
    Objects.requireNonNull(decision, "decision is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 64) {
      throw new IllegalArgumentException("policyVersion is required and bounded");
    }
    if (observedAmount.signum() < 0 || reservedAmount.signum() < 0) {
      throw new IllegalArgumentException("budget amounts must be non-negative");
    }
  }
}
