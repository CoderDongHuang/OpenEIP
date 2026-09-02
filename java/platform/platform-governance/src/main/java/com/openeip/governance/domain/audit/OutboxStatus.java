package com.openeip.governance.domain.audit;

/** Delivery state for a transactional outbox entry. */
public enum OutboxStatus {
  PENDING,
  DELIVERED,
  QUARANTINED
}
