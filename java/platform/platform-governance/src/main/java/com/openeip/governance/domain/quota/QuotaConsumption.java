package com.openeip.governance.domain.quota;

import java.math.BigDecimal;
import java.util.Objects;

/** Observed usage and active reservations at an admission boundary. */
public record QuotaConsumption(
    long tokenUnits, BigDecimal costAmount, long requestUnits, int concurrencyUnits) {
  public static final QuotaConsumption ZERO = new QuotaConsumption(0, BigDecimal.ZERO, 0, 0);

  public QuotaConsumption {
    Objects.requireNonNull(costAmount, "costAmount is required");
    if (tokenUnits < 0 || costAmount.signum() < 0 || requestUnits < 0 || concurrencyUnits < 0) {
      throw new IllegalArgumentException("quota consumption cannot be negative");
    }
  }

  public QuotaConsumption reserve(QuotaAdmissionRequest request) {
    try {
      return new QuotaConsumption(
          Math.addExact(tokenUnits, request.requestedTokens()),
          costAmount.add(request.requestedCost()),
          Math.addExact(requestUnits, 1),
          Math.addExact(concurrencyUnits, request.concurrencyUnits()));
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("quota consumption exceeds supported bounds", exception);
    }
  }

  public boolean within(QuotaLimits limits) {
    return (limits.tokenLimit() == null || tokenUnits <= limits.tokenLimit())
        && (limits.costLimit() == null || costAmount.compareTo(limits.costLimit()) <= 0)
        && (limits.requestLimit() == null || requestUnits <= limits.requestLimit())
        && (limits.concurrencyLimit() == null || concurrencyUnits <= limits.concurrencyLimit());
  }
}
