package com.openeip.governance.infrastructure.config;

import com.openeip.governance.application.catalog.PromptContentCipher;
import com.openeip.governance.application.context.TenantContextResolver;
import com.openeip.governance.application.context.TenantMembershipPort;
import com.openeip.governance.infrastructure.policy.AesGcmPromptContentCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires Governance context resolution after its persistence adapter is available. */
@Configuration
public class GovernanceConfiguration {
  @Bean
  public TenantContextResolver tenantContextResolver(TenantMembershipPort memberships) {
    return new TenantContextResolver(memberships);
  }

  @Bean
  @ConditionalOnProperty(name = "openeip.governance.prompt-encryption-key-base64")
  public PromptContentCipher promptContentCipher(
      @Value("${openeip.governance.prompt-encryption-key-base64}") String key) {
    return new AesGcmPromptContentCipher(key);
  }
}
