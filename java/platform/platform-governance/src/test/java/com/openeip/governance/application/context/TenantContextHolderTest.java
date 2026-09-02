package com.openeip.governance.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextHolderTest {
  @AfterEach
  void clearContext() {
    TenantContextHolder.clear();
  }

  @Test
  void returnsBoundContextAndClearsIt() {
    TenantContext context = context();
    TenantContextHolder.bind(context);

    assertThat(TenantContextHolder.required()).isSameAs(context);
    TenantContextHolder.clear();
    assertThat(TenantContextHolder.current()).isEmpty();
    assertThatThrownBy(TenantContextHolder::required).hasMessage("Tenant context is not bound");
  }

  private static TenantContext context() {
    return new TenantContext(
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        null,
        Set.of("ROLE_OPERATOR"),
        "policy-1",
        "request-1",
        "trace-1",
        GovernanceScope.TENANT,
        Instant.parse("2026-09-02T12:00:00Z"));
  }
}
