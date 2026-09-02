package com.openeip.governance.domain.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sanitized event envelope waiting for at-least-once publication. */
public record OutboxEntry(
    UUID id,
    UUID tenantId,
    UUID aggregateId,
    UUID eventId,
    String eventType,
    String payloadJson,
    OutboxStatus status,
    int attempts,
    Instant createdAt,
    Instant deliveredAt) {

  public OutboxEntry {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(aggregateId, "aggregateId is required");
    Objects.requireNonNull(eventId, "eventId is required");
    Objects.requireNonNull(status, "status is required");
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts must not be negative");
    }
    if (payloadJson == null || payloadJson.isBlank()) {
      throw new IllegalArgumentException("payloadJson is required");
    }
  }
}
