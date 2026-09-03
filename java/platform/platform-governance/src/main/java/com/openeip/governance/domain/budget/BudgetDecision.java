package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable append-only budget authorization history entry. */
public record BudgetDecision(
    UUID id,
    UUID tenantId,
    UUID budgetId,
    UUID executionId,
    BudgetDecisionType type,
    String policyVersion,
    BigDecimal observedAmount,
    BigDecimal reservedAmount,
    BudgetDecisionOutcome decision,
    Instant createdAt) {}
