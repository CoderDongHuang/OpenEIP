package com.openeip.connector.spi;

import java.util.Map;

@FunctionalInterface
public interface SecretResolver {
  Map<String, String> resolve(String tenantId, String credentialRef);
}
