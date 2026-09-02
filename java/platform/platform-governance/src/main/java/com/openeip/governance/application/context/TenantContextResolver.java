package com.openeip.governance.application.context;

import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.SystemScopeGrant;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.domain.context.TenantMembership;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Derives immutable governance context from server-side membership and trusted correlation data.
 */
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Invalid context configuration must abort construction.")
public class TenantContextResolver {
  private static final String PLATFORM_ADMIN_ROLE = "ROLE_PLATFORM_ADMIN";
  private static final Duration DEFAULT_CONTEXT_TTL = Duration.ofMinutes(15);

  private final TenantMembershipPort memberships;
  private final Clock clock;
  private final Duration contextTtl;

  public TenantContextResolver(TenantMembershipPort memberships) {
    this(memberships, Clock.systemUTC(), DEFAULT_CONTEXT_TTL);
  }

  TenantContextResolver(TenantMembershipPort memberships, Clock clock, Duration contextTtl) {
    this.memberships = Objects.requireNonNull(memberships, "memberships are required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
    this.contextTtl = Objects.requireNonNull(contextTtl, "contextTtl is required");
    if (contextTtl.isNegative() || contextTtl.isZero()) {
      throw new IllegalArgumentException("contextTtl must be positive");
    }
  }

  /** Resolves the only tenant context selected by the server for a principal. */
  public TenantContext resolve(UUID principalId, String requestId, String traceId) {
    if (principalId == null) {
      throw GovernanceAuthorizationException.invalidContext("principalId is required");
    }
    requireCorrelation(requestId, "requestId");
    requireCorrelation(traceId, "traceId");
    TenantMembership membership =
        memberships
            .findActiveByPrincipal(principalId)
            .orElseThrow(
                () ->
                    GovernanceAuthorizationException.invalidContext(
                        "Active tenant membership not found"));
    if (!principalId.equals(membership.principalId())) {
      throw GovernanceAuthorizationException.invalidContext("Tenant membership principal mismatch");
    }
    Instant expiresAt = clock.instant().plus(contextTtl);
    return new TenantContext(
        membership.tenantId(),
        membership.organizationId(),
        membership.principalId(),
        membership.membershipId(),
        membership.roles(),
        membership.policyVersion(),
        requestId,
        traceId,
        GovernanceScope.TENANT,
        expiresAt);
  }

  /**
   * Resolves a short-lived system context only for an explicitly allowed platform administrator.
   */
  public TenantContext resolveSystem(SystemScopeGrant grant) {
    Objects.requireNonNull(grant, "grant is required");
    Instant now = clock.instant();
    if (!grant.roles().contains(PLATFORM_ADMIN_ROLE) || !grant.expiresAt().isAfter(now)) {
      throw GovernanceAuthorizationException.systemScopeNotPermitted();
    }
    return new TenantContext(
        grant.tenantId(),
        null,
        grant.principalId(),
        null,
        grant.roles(),
        grant.policyVersion(),
        grant.requestId(),
        grant.traceId(),
        GovernanceScope.SYSTEM,
        grant.expiresAt());
  }

  private static void requireCorrelation(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 256) {
      throw GovernanceAuthorizationException.invalidContext(field + " is required");
    }
  }
}
