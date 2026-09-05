package com.openeip.governance.domain.quota;

import java.time.Instant;
import java.util.Objects;

/** Complete immutable facts required to persist an admission decision. */
public record QuotaReservationRegistration(
    QuotaAdmissionRequest request,
    String policyVersion,
    QuotaWindow window,
    QuotaConsumption observed,
    QuotaDecisionOutcome decision,
    String requestId,
    String traceId,
    Instant admittedAt) {
  public QuotaReservationRegistration {
    Objects.requireNonNull(request, "request is required");
    Objects.requireNonNull(policyVersion, "policyVersion is required");
    Objects.requireNonNull(window, "window is required");
    Objects.requireNonNull(observed, "observed is required");
    Objects.requireNonNull(decision, "decision is required");
    Objects.requireNonNull(requestId, "requestId is required");
    Objects.requireNonNull(traceId, "traceId is required");
    Objects.requireNonNull(admittedAt, "admittedAt is required");
  }
}
