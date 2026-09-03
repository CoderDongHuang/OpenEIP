package com.openeip.governance.domain.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable provider/model pricing used to reproduce historical cost calculations. */
public record PricingSnapshot(
    UUID id,
    UUID tenantId,
    UUID providerId,
    UUID modelId,
    String version,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String currency,
    String roundingMode,
    Instant createdAt) {}
