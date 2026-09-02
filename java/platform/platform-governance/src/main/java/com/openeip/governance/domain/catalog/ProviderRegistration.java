package com.openeip.governance.domain.catalog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Input for a tenant-scoped provider policy; credentials are represented only by a reference. */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "The compact constructor creates immutable collection snapshots.")
public record ProviderRegistration(
    UUID tenantId,
    String name,
    Map<String, Object> endpointPolicy,
    String secretRef,
    Set<String> capabilities,
    Instant now) {

  public ProviderRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(endpointPolicy, "endpointPolicy is required");
    Objects.requireNonNull(capabilities, "capabilities are required");
    Objects.requireNonNull(now, "now is required");
    name = bounded(name, "name", 128);
    secretRef = bounded(secretRef, "secretRef", 256);
    if (!secretRef.startsWith("secret://") || secretRef.contains("\n")) {
      throw new IllegalArgumentException("secretRef must be a reference");
    }
    capabilities = Set.copyOf(capabilities);
    if (capabilities.size() > 32
        || capabilities.stream()
            .anyMatch(value -> value == null || value.isBlank() || value.length() > 64)) {
      throw new IllegalArgumentException("capabilities must be bounded non-blank values");
    }
    endpointPolicy = safeEndpointPolicy(endpointPolicy);
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }

  private static Map<String, Object> safeEndpointPolicy(Map<String, Object> values) {
    if (values.size() > 16) {
      throw new IllegalArgumentException("endpointPolicy is too large");
    }
    var result = new LinkedHashMap<String, Object>();
    for (var entry : values.entrySet()) {
      String key = bounded(entry.getKey(), "endpointPolicy key", 64);
      String normalized = key.toLowerCase(Locale.ROOT);
      if (normalized.contains("secret")
          || normalized.contains("password")
          || normalized.contains("token")
          || normalized.contains("authorization")
          || normalized.contains("credential")
          || normalized.contains("api-key")) {
        throw new IllegalArgumentException("endpointPolicy cannot contain credentials");
      }
      Object value = entry.getValue();
      if (value == null || value instanceof Boolean || value instanceof Number) {
        result.put(key, value);
      } else if (value instanceof String text && text.length() <= 512) {
        result.put(key, text);
      } else {
        throw new IllegalArgumentException("endpointPolicy values must be bounded scalars");
      }
    }
    return Collections.unmodifiableMap(result);
  }
}
