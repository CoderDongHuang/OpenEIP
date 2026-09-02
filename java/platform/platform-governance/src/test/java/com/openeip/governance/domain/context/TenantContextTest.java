package com.openeip.governance.domain.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextTest {
  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PRINCIPAL_ID = UUID.randomUUID();
  private static final Instant EXPIRES_AT = Instant.parse("2026-09-02T12:00:00Z");

  @Test
  void copiesRolesAndRejectsBlankContextFields() {
    Set<String> roles = new HashSet<>();
    roles.add("ROLE_OPERATOR");

    TenantContext context = context(roles);
    roles.add("ROLE_ADMIN");

    assertThat(context.roles()).containsExactly("ROLE_OPERATOR");
    assertThatThrownBy(() -> context(Set.of(" ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("roles must not contain blank values");
    assertThatThrownBy(
            () ->
                new TenantContext(
                    TENANT_ID,
                    null,
                    PRINCIPAL_ID,
                    null,
                    Set.of("ROLE_OPERATOR"),
                    " ",
                    "request-1",
                    "trace-1",
                    GovernanceScope.TENANT,
                    EXPIRES_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("policyVersion is required");
  }

  @Test
  void treatsExpiryAsFailClosedBoundary() {
    TenantContext context = context(Set.of("ROLE_OPERATOR"));

    assertThat(context.expiredAt(Instant.parse("2026-09-02T11:59:59Z"))).isFalse();
    assertThat(context.expiredAt(EXPIRES_AT)).isTrue();
    assertThat(context.expiredAt(Instant.parse("2026-09-02T12:00:01Z"))).isTrue();
  }

  @Test
  void preservesExplicitSystemScope() {
    TenantContext context =
        new TenantContext(
            TENANT_ID,
            null,
            PRINCIPAL_ID,
            null,
            Set.of("ROLE_PLATFORM_ADMIN"),
            "policy-1",
            "request-1",
            "trace-1",
            GovernanceScope.SYSTEM,
            EXPIRES_AT);

    assertThat(context.isSystemScope()).isTrue();
    assertThat(context.tenantId()).isEqualTo(TENANT_ID);
  }

  private static TenantContext context(Set<String> roles) {
    return new TenantContext(
        TENANT_ID,
        null,
        PRINCIPAL_ID,
        null,
        roles,
        "policy-1",
        "request-1",
        "trace-1",
        GovernanceScope.TENANT,
        EXPIRES_AT);
  }
}
