package com.openeip.governance.domain.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable normalized usage fact with its historical pricing result. */
public record UsageRecord(
    UUID id,
    UUID tenantId,
    UUID executionId,
    String providerRequestId,
    long usageRevision,
    UUID pricingSnapshotId,
    String unitType,
    long inputUnits,
    long outputUnits,
    String currency,
    String roundingMode,
    BigDecimal calculatedAmount,
    String requestId,
    String traceId,
    String sourceRef,
    Instant createdAt) {}
