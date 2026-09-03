package com.openeip.governance.domain.usage;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Normalized runtime usage fact submitted to the authoritative cost ledger. */
public record UsageRegistration(
    UUID tenantId,
    UUID executionId,
    String providerRequestId,
    long usageRevision,
    UUID pricingSnapshotId,
    String unitType,
    long inputUnits,
    long outputUnits,
    String requestId,
    String traceId,
    String sourceRef,
    Instant now) {

  public UsageRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(executionId, "executionId is required");
    Objects.requireNonNull(pricingSnapshotId, "pricingSnapshotId is required");
    Objects.requireNonNull(now, "now is required");
    providerRequestId = bounded(providerRequestId, "providerRequestId", 128);
    requestId = bounded(requestId, "requestId", 128);
    sourceRef = bounded(sourceRef, "sourceRef", 256);
    if (usageRevision < 0) {
      throw new IllegalArgumentException("usageRevision must be non-negative");
    }
    if (inputUnits < 0 || outputUnits < 0) {
      throw new IllegalArgumentException("usage units must be non-negative");
    }
    unitType = bounded(unitType, "unitType", 32).toUpperCase(Locale.ROOT);
    if (traceId == null || !traceId.matches("[a-f0-9]{16,32}")) {
      throw new IllegalArgumentException("traceId must be lowercase hexadecimal");
    }
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength || value.contains("\n")) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
