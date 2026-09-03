package com.openeip.governance.domain.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Validated input for creating a tenant budget policy. */
public record BudgetRegistration(
    UUID tenantId,
    String name,
    String currency,
    BigDecimal limitAmount,
    BudgetWindowType windowType,
    Instant now) {

  public BudgetRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(limitAmount, "limitAmount is required");
    Objects.requireNonNull(windowType, "windowType is required");
    Objects.requireNonNull(now, "now is required");
    name = bounded(name, "name", 128);
    currency = currency(currency);
    if (limitAmount.signum() <= 0
        || limitAmount.scale() > 6
        || limitAmount.precision() - limitAmount.scale() > 14) {
      throw new IllegalArgumentException("limitAmount must fit DECIMAL(20,6) and be positive");
    }
  }

  private static String currency(String value) {
    if (value == null || !value.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
    }
    return value.toUpperCase(Locale.ROOT);
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength || value.contains("\n")) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
