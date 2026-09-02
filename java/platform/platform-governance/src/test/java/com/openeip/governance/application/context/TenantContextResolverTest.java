package com.openeip.governance.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.governance.domain.context.SystemOperation;
import com.openeip.governance.domain.context.SystemScopeGrant;
import com.openeip.governance.domain.context.TenantMembership;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextResolverTest {
  private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");
  private static final UUID PRINCIPAL_ID = UUID.randomUUID();
  private static final UUID TENANT_ID = UUID.randomUUID();
  private final TenantMembership membership =
      new TenantMembership(
          UUID.randomUUID(),
          TENANT_ID,
          UUID.randomUUID(),
          PRINCIPAL_ID,
          Set.of("ROLE_OPERATOR"),
          "policy-7");

  @Test
  void derivesTenantContextFromMembershipAndNeverMutatesItsRoles() {
    TenantContextResolver resolver = resolver(id -> Optional.of(membership));

    var context = resolver.resolve(PRINCIPAL_ID, "request-1", "trace-1");

    assertThat(context.tenantId()).isEqualTo(TENANT_ID);
    assertThat(context.principalId()).isEqualTo(PRINCIPAL_ID);
    assertThat(context.membershipId()).isEqualTo(membership.membershipId());
    assertThat(context.roles()).containsExactly("ROLE_OPERATOR");
    assertThat(context.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void rejectsMissingOrMismatchedMembershipFailClosed() {
    TenantContextResolver missing = resolver(id -> Optional.empty());
    assertThatThrownBy(() -> missing.resolve(PRINCIPAL_ID, "request-1", "trace-1"))
        .isInstanceOfSatisfying(
            GovernanceAuthorizationException.class,
            exception -> {
              assertThat(exception).hasMessage("Active tenant membership not found");
              assertThat(exception.code())
                  .isEqualTo(GovernanceAuthorizationException.CONTEXT_ERROR_CODE);
            });

    TenantMembership mismatched =
        new TenantMembership(
            membership.membershipId(),
            TENANT_ID,
            membership.organizationId(),
            UUID.randomUUID(),
            membership.roles(),
            membership.policyVersion());
    TenantContextResolver resolver = resolver(id -> Optional.of(mismatched));
    assertThatThrownBy(() -> resolver.resolve(PRINCIPAL_ID, "request-1", "trace-1"))
        .isInstanceOf(GovernanceAuthorizationException.class)
        .hasMessage("Tenant membership principal mismatch");

    assertThatThrownBy(() -> missing.resolve(PRINCIPAL_ID, " ", "trace-1"))
        .isInstanceOfSatisfying(
            GovernanceAuthorizationException.class,
            exception -> {
              assertThat(exception).hasMessage("requestId is required");
              assertThat(exception.code())
                  .isEqualTo(GovernanceAuthorizationException.CONTEXT_ERROR_CODE);
            });
  }

  @Test
  void onlyAllowsUnexpiredPlatformAdministratorSystemGrant() {
    TenantContextResolver resolver = resolver(id -> Optional.empty());
    SystemScopeGrant grant =
        new SystemScopeGrant(
            TENANT_ID,
            PRINCIPAL_ID,
            Set.of("ROLE_PLATFORM_ADMIN"),
            "policy-7",
            "request-1",
            "trace-1",
            SystemOperation.MIGRATION,
            NOW.plusSeconds(30));

    assertThat(resolver.resolveSystem(grant).isSystemScope()).isTrue();
    assertThatThrownBy(
            () ->
                resolver.resolveSystem(
                    new SystemScopeGrant(
                        TENANT_ID,
                        PRINCIPAL_ID,
                        Set.of("ROLE_OPERATOR"),
                        "policy-7",
                        "request-1",
                        "trace-1",
                        SystemOperation.PLATFORM_ADMINISTRATION,
                        NOW.plusSeconds(30))))
        .isInstanceOfSatisfying(
            GovernanceAuthorizationException.class,
            exception -> {
              assertThat(exception).hasMessage("System scope is not permitted");
              assertThat(exception.code())
                  .isEqualTo(GovernanceAuthorizationException.SYSTEM_SCOPE_ERROR_CODE);
            });
    assertThatThrownBy(
            () ->
                resolver.resolveSystem(
                    new SystemScopeGrant(
                        TENANT_ID,
                        PRINCIPAL_ID,
                        Set.of("ROLE_PLATFORM_ADMIN"),
                        "policy-7",
                        "request-1",
                        "trace-1",
                        SystemOperation.MIGRATION,
                        NOW)))
        .isInstanceOfSatisfying(
            GovernanceAuthorizationException.class,
            exception -> {
              assertThat(exception).hasMessage("System scope is not permitted");
              assertThat(exception.code())
                  .isEqualTo(GovernanceAuthorizationException.SYSTEM_SCOPE_ERROR_CODE);
            });
  }

  private static TenantContextResolver resolver(TenantMembershipPort port) {
    return new TenantContextResolver(
        port, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(15));
  }
}
