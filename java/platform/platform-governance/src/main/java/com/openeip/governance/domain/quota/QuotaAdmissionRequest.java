package com.openeip.governance.domain.quota;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded request for one runtime quota admission and lease. */
public record QuotaAdmissionRequest(
    UUID tenantId,
    UUID quotaPolicyId,
    UUID executionId,
    String idempotencyKey,
    long requestedTokens,
    BigDecimal requestedCost,
    int concurrencyUnits,
    Instant expiresAt) {
  private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9._:-]{16,128}$");

  public QuotaAdmissionRequest {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(quotaPolicyId, "quotaPolicyId is required");
    Objects.requireNonNull(executionId, "executionId is required");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
    Objects.requireNonNull(requestedCost, "requestedCost is required");
    Objects.requireNonNull(expiresAt, "expiresAt is required");
    if (!SAFE_KEY.matcher(idempotencyKey).matches()) {
      throw new IllegalArgumentException("idempotencyKey must be 16-128 safe characters");
    }
    if (requestedTokens < 0
        || requestedCost.signum() < 0
        || requestedCost.scale() > 6
        || requestedCost.precision() - requestedCost.scale() > 14) {
      throw new IllegalArgumentException("requested quota must be non-negative and bounded");
    }
    if (concurrencyUnits < 0 || concurrencyUnits > 1) {
      throw new IllegalArgumentException("concurrencyUnits must be zero or one");
    }
  }
}
