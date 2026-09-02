package com.openeip.governance.infrastructure.config;

import com.openeip.governance.application.context.TenantContextResolver;
import com.openeip.governance.application.context.TenantMembershipPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Governance context resolution after its persistence adapter is available. */
@Configuration
public class GovernanceConfiguration {
  @Bean
  public TenantContextResolver tenantContextResolver(TenantMembershipPort memberships) {
    return new TenantContextResolver(memberships);
  }
}
