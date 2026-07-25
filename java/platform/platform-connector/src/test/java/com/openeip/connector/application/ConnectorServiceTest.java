package com.openeip.connector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.repository.ConnectorInstanceRepository;
import com.openeip.connector.shared.ConnectorException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
  private static final String USER = "user-1";
  private static final String ID = UUID.randomUUID().toString();

  @Mock private ConnectorInstanceRepository repository;
  private ConnectorService service;

  @BeforeEach
  void setUp() {
    service =
        new ConnectorService(repository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createsPausedConnectorWithSecretReference() {
    when(repository.existsByTenantIdAndOwnerIdAndNameAndDeletedAtIsNull("default", USER, "db"))
        .thenReturn(false);
    when(repository.save(any(ConnectorInstance.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ConnectorInstance result =
        service.create(
            USER,
            " db ",
            ConnectorType.MYSQL,
            json("{\"host\":\"mysql\",\"port\":3306}"),
            "secret://connector/db");

    assertThat(result.getName()).isEqualTo("db");
    assertThat(result.getStatus()).isEqualTo(ConnectorStatus.PAUSED);
    assertThat(result.getCredentialRef()).isEqualTo("secret://connector/db");
    assertThat(result.getConfigJson()).contains("mysql");
  }

  @Test
  void rejectsSecretMaterialAndMissingRequiredConfig() {
    assertThatThrownBy(
            () ->
                service.create(
                    USER,
                    "db",
                    ConnectorType.MYSQL,
                    json("{\"host\":\"mysql\",\"password\":\"plain\"}"),
                    null))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Secrets");
    assertThatThrownBy(() -> service.create(USER, "kafka", ConnectorType.KAFKA, json("{}"), null))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("bootstrapServers");
    verify(repository, never()).save(any());
  }

  @Test
  void enforcesOwnerAndLifecycleRules() {
    ConnectorInstance instance =
        new ConnectorInstance(
            ID, "default", USER, "db", ConnectorType.MYSQL, "{\"host\":\"mysql\"}", null, NOW);
    when(repository.findByIdAndTenantIdAndDeletedAtIsNull(ID, "default"))
        .thenReturn(Optional.of(instance));

    assertThatThrownBy(() -> service.get("other-user", ID))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("forbidden");
    assertThat(service.setStatus(USER, ID, ConnectorStatus.ACTIVE).getStatus())
        .isEqualTo(ConnectorStatus.ACTIVE);
    assertThat(service.setStatus(USER, ID, ConnectorStatus.PAUSED).getStatus())
        .isEqualTo(ConnectorStatus.PAUSED);
    assertThatThrownBy(() -> service.setStatus(USER, ID, ConnectorStatus.ERROR))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("health check");
  }

  @Test
  void validatesIdentifiersAndCredentialReferences() {
    assertThatThrownBy(() -> service.get(USER, "bad-id"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("identifier");
    assertThatThrownBy(
            () ->
                service.create(
                    USER, "db", ConnectorType.MYSQL, json("{\"host\":\"mysql\"}"), "raw-secret"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("secret://");
  }

  private static com.fasterxml.jackson.databind.JsonNode json(String value) {
    try {
      return new ObjectMapper().readTree(value);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
