package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable threshold crossing notification record. */
public record BudgetAlert(
    UUID id,
    UUID tenantId,
    UUID budgetId,
    Instant windowStart,
    BigDecimal threshold,
    long crossingRevision,
    BudgetAlertStatus status,
    Instant createdAt) {}
