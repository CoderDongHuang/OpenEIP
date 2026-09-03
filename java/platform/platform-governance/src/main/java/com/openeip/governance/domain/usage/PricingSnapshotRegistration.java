package com.openeip.governance.domain.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Input for an immutable tenant-scoped provider/model price version. */
public record PricingSnapshotRegistration(
    UUID tenantId,
    UUID providerId,
    UUID modelId,
    String version,
    BigDecimal inputUnitPrice,
    BigDecimal outputUnitPrice,
    String currency,
    String roundingMode,
    Instant now) {

  public PricingSnapshotRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(providerId, "providerId is required");
    Objects.requireNonNull(modelId, "modelId is required");
    Objects.requireNonNull(inputUnitPrice, "inputUnitPrice is required");
    Objects.requireNonNull(outputUnitPrice, "outputUnitPrice is required");
    Objects.requireNonNull(now, "now is required");
    version = bounded(version, "version", 64);
    inputUnitPrice = price(inputUnitPrice, "inputUnitPrice");
    outputUnitPrice = price(outputUnitPrice, "outputUnitPrice");
    currency = currency(currency);
    roundingMode = roundingMode(roundingMode);
  }

  private static BigDecimal price(BigDecimal value, String field) {
    if (value.signum() < 0 || value.scale() > 10 || value.precision() - value.scale() > 10) {
      throw new IllegalArgumentException(field + " must fit DECIMAL(20,10) and be non-negative");
    }
    return value;
  }

  private static String currency(String value) {
    if (value == null || !value.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
    }
    return value;
  }

  private static String roundingMode(String value) {
    String normalized = bounded(value, "roundingMode", 32).toUpperCase(Locale.ROOT);
    try {
      RoundingMode.valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("roundingMode is unsupported", exception);
    }
    return normalized;
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength || value.contains("\n")) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
