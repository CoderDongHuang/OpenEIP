package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot of a tenant budget policy. */
public record Budget(
    UUID id,
    UUID tenantId,
    String name,
    String currency,
    BigDecimal limitAmount,
    BudgetWindowType windowType,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
