package com.openeip.connector.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.shared.ConnectorException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentSecretResolverTest {
  @Test
  void resolvesJsonObjectWithoutExposingEnvironmentName() {
    EnvironmentSecretResolver resolver =
        new EnvironmentSecretResolver(
            new ObjectMapper(),
            name ->
                name.equals("OPENEIP_CONNECTOR_SECRET_DATABASE")
                    ? "{\"username\":\"u\",\"password\":\"p\"}"
                    : null);
    assertThat(resolver.resolve("tenant", "secret://env/DATABASE"))
        .isEqualTo(Map.of("username", "u", "password", "p"));
    assertThat(resolver.resolve("tenant", null)).isEmpty();
  }

  @Test
  void rejectsUnsupportedMissingAndMalformedCredentials() {
    EnvironmentSecretResolver missing =
        new EnvironmentSecretResolver(new ObjectMapper(), name -> null);
    assertThatThrownBy(() -> missing.resolve("tenant", "secret://vault/key"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> missing.resolve("tenant", "secret://env/MISSING"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("not configured");
    assertInvalid("[]", "JSON object");
    assertInvalid("{\"port\":123}", "strings");
    assertInvalid("not-json", "invalid JSON");
  }

  private static void assertInvalid(String value, String message) {
    EnvironmentSecretResolver resolver =
        new EnvironmentSecretResolver(new ObjectMapper(), name -> value);
    assertThatThrownBy(() -> resolver.resolve("tenant", "secret://env/BAD"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining(message);
  }
}
