package com.openeip.governance.domain.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Append-only audit evidence with a tenant-scoped integrity link. */
public record AuditRecord(
    UUID id,
    UUID eventId,
    UUID tenantId,
    UUID principalId,
    String action,
    String resourceType,
    String resourceId,
    AuditOutcome outcome,
    String requestId,
    String traceId,
    String policyVersion,
    String schemaVersion,
    Instant occurredAt,
    String previousHash,
    String recordHash,
    Instant retentionDeadline,
    Map<String, Object> summary) {

  public AuditRecord {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(eventId, "eventId is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(principalId, "principalId is required");
    Objects.requireNonNull(outcome, "outcome is required");
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    if (recordHash == null || recordHash.length() != 64) {
      throw new IllegalArgumentException("recordHash must be a SHA-256 hex value");
    }
    summary =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(summary, "summary is required")));
  }
}
