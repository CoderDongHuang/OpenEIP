package com.openeip.governance.domain.context;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-created, time-limited authorization for an explicit system operation. */
public record SystemScopeGrant(
    UUID tenantId,
    UUID principalId,
    Set<String> roles,
    String policyVersion,
    String requestId,
    String traceId,
    SystemOperation operation,
    Instant expiresAt) {

  public SystemScopeGrant {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(principalId, "principalId is required");
    Objects.requireNonNull(roles, "roles are required");
    Objects.requireNonNull(operation, "operation is required");
    Objects.requireNonNull(expiresAt, "expiresAt is required");
    roles = Set.copyOf(roles);
    if (policyVersion == null || policyVersion.isBlank()) {
      throw new IllegalArgumentException("policyVersion is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (traceId == null || traceId.isBlank()) {
      throw new IllegalArgumentException("traceId is required");
    }
  }
}
