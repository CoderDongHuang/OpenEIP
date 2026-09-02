package com.openeip.governance.domain.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable input for an audit append. The summary is sanitized before persistence. */
public record AuditAppendCommand(
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
    Instant retentionDeadline,
    Map<String, Object> summary) {

  public AuditAppendCommand {
    Objects.requireNonNull(eventId, "eventId is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(principalId, "principalId is required");
    Objects.requireNonNull(outcome, "outcome is required");
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    action = requiredText(action, "action", 96);
    resourceType = requiredText(resourceType, "resourceType", 64);
    resourceId = requiredText(resourceId, "resourceId", 128);
    requestId = requiredText(requestId, "requestId", 128);
    traceId = requiredText(traceId, "traceId", 32);
    policyVersion = requiredText(policyVersion, "policyVersion", 64);
    schemaVersion = requiredText(schemaVersion, "schemaVersion", 32);
    summary =
        Collections.unmodifiableMap(
            new LinkedHashMap<>(Objects.requireNonNull(summary, "summary is required")));
  }

  private static String requiredText(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
