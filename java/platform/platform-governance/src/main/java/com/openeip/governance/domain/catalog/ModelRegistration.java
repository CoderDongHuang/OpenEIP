package com.openeip.governance.domain.catalog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Input for a model draft and its first immutable version. */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "The compact constructor creates immutable set snapshots.")
public record ModelRegistration(
    UUID tenantId,
    UUID providerId,
    String name,
    String contentDigest,
    Set<String> capabilities,
    Set<String> routingLabels,
    UUID pricingSnapshotId,
    Instant now) {

  public ModelRegistration {
    if (tenantId == null || providerId == null || now == null) {
      throw new IllegalArgumentException("tenantId, providerId, and now are required");
    }
    name = bounded(name, "name", 128);
    if (contentDigest == null || !contentDigest.matches("sha256:[a-f0-9]{64}")) {
      throw new IllegalArgumentException("contentDigest must be a SHA-256 digest");
    }
    capabilities = boundedSet(capabilities, "capabilities", 32);
    routingLabels = boundedSet(routingLabels, "routingLabels", 32);
  }

  private static Set<String> boundedSet(Set<String> values, String field, int maxItems) {
    if (values == null || values.size() > maxItems) {
      throw new IllegalArgumentException(field + " is missing or too large");
    }
    if (values.stream()
        .anyMatch(value -> value == null || value.isBlank() || value.length() > 64)) {
      throw new IllegalArgumentException(field + " contains an invalid value");
    }
    return Set.copyOf(values);
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
