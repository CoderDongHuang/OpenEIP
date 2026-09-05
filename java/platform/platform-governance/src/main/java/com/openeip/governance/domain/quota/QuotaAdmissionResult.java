package com.openeip.governance.domain.quota;

/** Result of quota admission. Denials are retained as idempotent history. */
public record QuotaAdmissionResult(
    QuotaReservation reservation, boolean duplicate, String errorCode) {
  public boolean allowed() {
    return reservation.decision() == QuotaDecisionOutcome.ALLOW;
  }
}
