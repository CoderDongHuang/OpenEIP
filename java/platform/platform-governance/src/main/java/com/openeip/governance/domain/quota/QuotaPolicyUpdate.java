package com.openeip.governance.domain.quota;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Validated optimistic-lock update for a tenant quota policy. */
public record QuotaPolicyUpdate(
    UUID tenantId,
    UUID quotaPolicyId,
    String name,
    QuotaLimits limits,
    QuotaWindowType windowType,
    long expectedRevision,
    Instant now) {

  public QuotaPolicyUpdate {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(quotaPolicyId, "quotaPolicyId is required");
    Objects.requireNonNull(limits, "limits are required");
    Objects.requireNonNull(windowType, "windowType is required");
    Objects.requireNonNull(now, "now is required");
    if (expectedRevision < 0) {
      throw new IllegalArgumentException("expectedRevision must be non-negative");
    }
    if (name == null || name.isBlank() || name.length() > 128 || name.contains("\n")) {
      throw new IllegalArgumentException("name is required and bounded");
    }
  }
}
