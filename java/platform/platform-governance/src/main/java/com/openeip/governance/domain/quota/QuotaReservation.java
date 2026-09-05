package com.openeip.governance.domain.quota;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persisted, idempotent runtime quota admission decision. */
public record QuotaReservation(
    UUID id,
    UUID tenantId,
    UUID quotaPolicyId,
    UUID executionId,
    String idempotencyKey,
    String policyVersion,
    QuotaWindow window,
    long requestedTokens,
    BigDecimal requestedCost,
    int concurrencyUnits,
    QuotaConsumption observed,
    QuotaDecisionOutcome decision,
    String requestId,
    String traceId,
    Instant expiresAt,
    Instant releasedAt,
    Instant createdAt) {

  public boolean sameFacts(QuotaAdmissionRequest request) {
    return tenantId.equals(request.tenantId())
        && quotaPolicyId.equals(request.quotaPolicyId())
        && executionId.equals(request.executionId())
        && idempotencyKey.equals(request.idempotencyKey())
        && requestedTokens == request.requestedTokens()
        && requestedCost.compareTo(request.requestedCost()) == 0
        && concurrencyUnits == request.concurrencyUnits()
        && expiresAt.equals(request.expiresAt());
  }

  public boolean activeAt(Instant instant) {
    return decision == QuotaDecisionOutcome.ALLOW
        && releasedAt == null
        && expiresAt.isAfter(instant);
  }
}
