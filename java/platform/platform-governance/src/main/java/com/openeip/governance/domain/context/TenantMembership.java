package com.openeip.governance.domain.context;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-side membership data used to construct a tenant context. */
public record TenantMembership(
    UUID membershipId,
    UUID tenantId,
    UUID organizationId,
    UUID principalId,
    Set<String> roles,
    String policyVersion) {

  public TenantMembership {
    Objects.requireNonNull(membershipId, "membershipId is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(principalId, "principalId is required");
    Objects.requireNonNull(roles, "roles are required");
    if (roles.stream().anyMatch(role -> role == null || role.isBlank())) {
      throw new IllegalArgumentException("roles must not contain blank values");
    }
    roles = Set.copyOf(roles);
    if (policyVersion == null || policyVersion.isBlank()) {
      throw new IllegalArgumentException("policyVersion is required");
    }
  }
}
