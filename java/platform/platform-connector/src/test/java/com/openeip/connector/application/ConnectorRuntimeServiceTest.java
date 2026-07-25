package com.openeip.connector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.entity.ConnectorOperation;
import com.openeip.connector.domain.repository.ConnectorInstanceRepository;
import com.openeip.connector.domain.repository.ConnectorOperationRepository;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.shared.ConnectorException;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.ConnectorRegistry;
import com.openeip.connector.spi.ConnectorSpi;
import com.openeip.connector.spi.DataReader;
import com.openeip.connector.spi.DataWriter;
import com.openeip.connector.spi.MetadataSchema;
import com.openeip.connector.spi.SecretResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorRuntimeServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
  private static final String ACTOR = "actor-1";
  private static final String CONNECTOR_ID = UUID.randomUUID().toString();

  @Mock private ConnectorService connectorService;
  @Mock private ConnectorInstanceRepository connectors;
  @Mock private ConnectorOperationRepository operations;
  @Mock private ConnectorRegistry registry;
  @Mock private SecretResolver secrets;
  @Mock private WebhookInboundService webhooks;
  private final ObjectMapper mapper = new ObjectMapper();
  private ConnectorRuntimeService service;
  private ConnectorInstance instance;
  private ConnectorSpi spi;

  @BeforeEach
  void setUp() {
    instance =
        new ConnectorInstance(
            CONNECTOR_ID,
            ConnectorService.TENANT,
            ACTOR,
            "database",
            ConnectorType.MYSQL,
            "{\"host\":\"db\"}",
            "secret://env/DB",
            NOW);
    instance.activate(NOW);
    spi = new FixtureSpi();
    lenient().when(connectorService.get(ACTOR, CONNECTOR_ID)).thenReturn(instance);
    lenient()
        .when(secrets.resolve(ConnectorService.TENANT, "secret://env/DB"))
        .thenReturn(Map.of("username", "u", "password", "p"));
    lenient().when(registry.require(ConnectorType.MYSQL)).thenReturn(spi);
    lenient().when(registry.metadata()).thenReturn(List.of(spi.getMetadata()));
    lenient()
        .when(operations.save(any(ConnectorOperation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new ConnectorRuntimeService(
            connectorService,
            connectors,
            operations,
            registry,
            secrets,
            webhooks,
            mapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void runsCatalogTestMetadataReadAndWrite() {
    assertThat(service.catalog()).hasSize(1);
    assertThat(service.test(ACTOR, CONNECTOR_ID, "corr").success()).isTrue();
    assertThat(service.metadata(ACTOR, CONNECTOR_ID, null).resources()).hasSize(1);
    assertThat(
            service.read(ACTOR, CONNECTOR_ID, null, "rows", mapper.createObjectNode(), 10).items())
        .hasSize(1);
    DataWriter.WriteResult result =
        service.write(
            ACTOR,
            CONNECTOR_ID,
            null,
            "write-1",
            "rows",
            "INSERT",
            mapper.createObjectNode().put("name", "one"));
    assertThat(result.status()).isEqualTo("STORED");
  }

  @Test
  void replaysSuccessfulIdempotentWriteAndRejectsInvalidRequests() throws Exception {
    ConnectorOperation previous =
        new ConnectorOperation(
            "operation-1",
            ConnectorService.TENANT,
            CONNECTOR_ID,
            ACTOR,
            "WRITE",
            "corr",
            "same-key",
            "fingerprint",
            NOW);
    previous.succeed("{\"resourceId\":\"row-1\",\"status\":\"STORED\"}", NOW);
    when(operations.findByTenantIdAndConnectorIdAndOperationTypeAndIdempotencyKey(
            ConnectorService.TENANT, CONNECTOR_ID, "WRITE", "same-key"))
        .thenReturn(Optional.of(previous));
    assertThat(
            service.write(
                ACTOR, CONNECTOR_ID, null, "same-key", "rows", "INSERT", mapper.createObjectNode()))
        .isEqualTo(new DataWriter.WriteResult("row-1", "STORED"));
    assertThatThrownBy(
            () -> service.read(ACTOR, CONNECTOR_ID, null, "", mapper.createObjectNode(), 10))
        .isInstanceOf(ConnectorException.class);
    assertThatThrownBy(
            () ->
                service.write(
                    ACTOR, CONNECTOR_ID, null, "", "rows", "INSERT", mapper.createObjectNode()))
        .isInstanceOf(ConnectorException.class);
  }

  @Test
  void rejectsPausedConnectorAndOversizedRequest() {
    instance.pause(NOW);
    assertThatThrownBy(() -> service.metadata(ACTOR, CONNECTOR_ID, null))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("active");
    instance.activate(NOW);
    JsonNode oversized = mapper.createObjectNode().put("payload", "x".repeat(1_100_000));
    assertThatThrownBy(
            () -> service.write(ACTOR, CONNECTOR_ID, null, "large", "rows", "INSERT", oversized))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("1 MiB");
  }

  @Test
  void recordsAdapterFailuresAndRejectsReplayOfFailedWrite() {
    ((FixtureSpi) spi).testResult = ConnectionTestResult.failure(1, "CONN-TEST", "offline");
    assertThat(service.test(ACTOR, CONNECTOR_ID, null).success()).isFalse();
    instance.activate(NOW);
    ((FixtureSpi) spi).readerFailure = true;
    assertThatThrownBy(
            () -> service.read(ACTOR, CONNECTOR_ID, null, "rows", mapper.createObjectNode(), 1))
        .isInstanceOf(ConnectorException.class);

    ConnectorOperation failed =
        new ConnectorOperation(
            "failed-operation",
            ConnectorService.TENANT,
            CONNECTOR_ID,
            ACTOR,
            "WRITE",
            "corr",
            "failed-key",
            "fingerprint",
            NOW);
    failed.fail("CONN-TEST", "failed", NOW);
    when(operations.findByTenantIdAndConnectorIdAndOperationTypeAndIdempotencyKey(
            ConnectorService.TENANT, CONNECTOR_ID, "WRITE", "failed-key"))
        .thenReturn(Optional.of(failed));
    assertThatThrownBy(
            () ->
                service.write(
                    ACTOR,
                    CONNECTOR_ID,
                    null,
                    "failed-key",
                    "rows",
                    "INSERT",
                    mapper.createObjectNode()))
        .isInstanceOf(ConnectorException.class);
  }

  private static final class FixtureSpi implements ConnectorSpi {
    private final ConnectorMetadata metadata =
        new ConnectorMetadata(ConnectorType.MYSQL, "Fixture", "1.0", "fixture", true, true);
    private ConnectionTestResult testResult = ConnectionTestResult.success(1);
    private boolean readerFailure;

    @Override
    public ConnectorMetadata getMetadata() {
      return metadata;
    }

    @Override
    public List<ConfigField> getConfigSchema() {
      return List.of();
    }

    @Override
    public ConnectionTestResult testConnection(ConnectorConfig config) {
      return testResult;
    }

    @Override
    public MetadataSchema extractMetadata(ConnectorConfig config) {
      return new MetadataSchema(
          List.of(new MetadataSchema.ResourceSchema("rows", "table", List.of("name"))));
    }

    @Override
    public DataReader createReader(ConnectorConfig config) {
      return request -> {
        if (readerFailure) {
          throw new ConnectorAdapterException("CONN-READ", "reader failed", false);
        }
        return new DataReader.ReadResult(
            List.of(new ObjectMapper().createObjectNode().put("name", "one")), null);
      };
    }

    @Override
    public Optional<DataWriter> createWriter(ConnectorConfig config) {
      return Optional.of(request -> new DataWriter.WriteResult("row-1", "STORED"));
    }
  }
}
