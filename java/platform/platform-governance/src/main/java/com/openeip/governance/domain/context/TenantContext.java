package com.openeip.governance.domain.context;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable, server-derived context used to enforce governance boundaries. */
public record TenantContext(
    UUID tenantId,
    UUID organizationId,
    UUID principalId,
    UUID membershipId,
    Set<String> roles,
    String policyVersion,
    String requestId,
    String traceId,
    GovernanceScope scope,
    Instant expiresAt) {

  public TenantContext {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(principalId, "principalId is required");
    Objects.requireNonNull(roles, "roles are required");
    Objects.requireNonNull(scope, "scope is required");
    Objects.requireNonNull(expiresAt, "expiresAt is required");
    if (roles.stream().anyMatch(role -> role == null || role.isBlank())) {
      throw new IllegalArgumentException("roles must not contain blank values");
    }
    roles = Set.copyOf(roles);
    policyVersion = requiredText(policyVersion, "policyVersion");
    requestId = requiredText(requestId, "requestId");
    traceId = requiredText(traceId, "traceId");
  }

  /** Returns true when the context cannot authorize work at the supplied instant. */
  public boolean expiredAt(Instant now) {
    Objects.requireNonNull(now, "now is required");
    return !now.isBefore(expiresAt);
  }

  public boolean isSystemScope() {
    return scope == GovernanceScope.SYSTEM;
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
