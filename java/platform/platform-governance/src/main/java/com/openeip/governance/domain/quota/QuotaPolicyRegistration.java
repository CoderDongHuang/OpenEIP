package com.openeip.governance.domain.quota;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Validated input for creating a tenant quota policy. */
public record QuotaPolicyRegistration(
    UUID tenantId, String name, QuotaLimits limits, QuotaWindowType windowType, Instant now) {

  public QuotaPolicyRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(limits, "limits are required");
    Objects.requireNonNull(windowType, "windowType is required");
    Objects.requireNonNull(now, "now is required");
    name = bounded(name, "name", 128);
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength || value.contains("\n")) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
