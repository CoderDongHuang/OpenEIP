package com.openeip.connector.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.entity.ConnectorOperation;
import com.openeip.connector.domain.entity.WebhookDelivery;
import com.openeip.connector.domain.repository.ConnectorInstanceRepository;
import com.openeip.connector.domain.repository.ConnectorOperationRepository;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.shared.ConnectorException;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.ConnectorRegistry;
import com.openeip.connector.spi.DataReader.ReadRequest;
import com.openeip.connector.spi.DataReader.ReadResult;
import com.openeip.connector.spi.DataWriter.WriteRequest;
import com.openeip.connector.spi.DataWriter.WriteResult;
import com.openeip.connector.spi.MetadataSchema;
import com.openeip.connector.spi.SecretResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConnectorRuntimeService {
  private static final int MAX_REQUEST_BYTES = 1_048_576;
  private final ConnectorService connectorService;
  private final ConnectorInstanceRepository connectors;
  private final ConnectorOperationRepository operations;
  private final ConnectorRegistry registry;
  private final SecretResolver secrets;
  private final WebhookInboundService webhooks;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public ConnectorRuntimeService(
      ConnectorService connectorService,
      ConnectorInstanceRepository connectors,
      ConnectorOperationRepository operations,
      ConnectorRegistry registry,
      SecretResolver secrets,
      WebhookInboundService webhooks,
      ObjectMapper mapper) {
    this(
        connectorService,
        connectors,
        operations,
        registry,
        secrets,
        webhooks,
        mapper,
        Clock.systemUTC());
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application scoped.")
  ConnectorRuntimeService(
      ConnectorService connectorService,
      ConnectorInstanceRepository connectors,
      ConnectorOperationRepository operations,
      ConnectorRegistry registry,
      SecretResolver secrets,
      WebhookInboundService webhooks,
      ObjectMapper mapper,
      Clock clock) {
    this.connectorService = connectorService;
    this.connectors = connectors;
    this.operations = operations;
    this.registry = registry;
    this.secrets = secrets;
    this.webhooks = webhooks;
    this.mapper = mapper;
    this.clock = clock;
  }

  public List<CatalogEntry> catalog() {
    return registry.metadata().stream()
        .map(
            metadata ->
                new CatalogEntry(metadata, registry.require(metadata.type()).getConfigSchema()))
        .toList();
  }

  public ConnectionTestResult test(String actorId, String connectorId, String correlationId) {
    ConnectorInstance instance = connectorService.get(actorId, connectorId);
    ConnectorConfig config = config(instance);
    ConnectorOperation operation =
        start(instance, actorId, "TEST", correlationId, null, config.values());
    try {
      ConnectionTestResult result = registry.require(instance.getType()).testConnection(config);
      if (result.success()) {
        instance.healthSuccess(clock.instant());
        operation.succeed(write(result), clock.instant());
      } else {
        instance.healthFailure(result.message(), clock.instant());
        operation.fail(result.code(), result.message(), clock.instant());
      }
      connectors.save(instance);
      operations.save(operation);
      return result;
    } catch (RuntimeException exception) {
      fail(instance, operation, exception);
      throw normalized(exception);
    }
  }

  public MetadataSchema metadata(String actorId, String connectorId, String correlationId) {
    ConnectorInstance instance = active(actorId, connectorId);
    return execute(
        instance,
        actorId,
        "METADATA",
        correlationId,
        null,
        mapper.createObjectNode(),
        () -> registry.require(instance.getType()).extractMetadata(config(instance)));
  }

  public ReadResult read(
      String actorId,
      String connectorId,
      String correlationId,
      String resource,
      JsonNode query,
      int limit) {
    if (limit < 1 || limit > 1000 || resource == null || resource.isBlank()) {
      throw ConnectorException.invalid("Invalid connector read request");
    }
    ConnectorInstance instance = active(actorId, connectorId);
    if (instance.getType() == com.openeip.connector.domain.ConnectorType.WEBHOOK
        && "deliveries".equals(resource)) {
      return new ReadResult(
          webhooks.recent(connectorId, limit).stream().map(this::delivery).toList(), null);
    }
    ReadRequest request = new ReadRequest(resource, query, limit);
    return execute(
        instance,
        actorId,
        "READ",
        correlationId,
        null,
        query,
        () -> registry.require(instance.getType()).createReader(config(instance)).read(request));
  }

  public WriteResult write(
      String actorId,
      String connectorId,
      String correlationId,
      String idempotencyKey,
      String resource,
      String action,
      JsonNode data) {
    if (resource == null || resource.isBlank() || action == null || action.isBlank()) {
      throw ConnectorException.invalid("Invalid connector write request");
    }
    validIdempotencyKey(idempotencyKey);
    ConnectorInstance instance = active(actorId, connectorId);
    var previous =
        operations.findByTenantIdAndConnectorIdAndOperationTypeAndIdempotencyKey(
            ConnectorService.TENANT, connectorId, "WRITE", idempotencyKey);
    if (previous.isPresent()) {
      if ("SUCCEEDED".equals(previous.get().getStatus())) {
        try {
          return mapper.readValue(previous.get().getResultJson(), WriteResult.class);
        } catch (Exception exception) {
          throw new IllegalStateException("Stored connector result is invalid", exception);
        }
      }
      throw ConnectorException.conflict(
          "A connector write with this Idempotency-Key is in progress or failed");
    }
    var writer =
        registry
            .require(instance.getType())
            .createWriter(config(instance))
            .orElseThrow(() -> ConnectorException.conflict("Connector is read-only"));
    WriteRequest request = new WriteRequest(resource, action, data, idempotencyKey);
    return execute(
        instance,
        actorId,
        "WRITE",
        correlationId,
        idempotencyKey,
        data,
        () -> writer.write(request));
  }

  private <T> T execute(
      ConnectorInstance instance,
      String actorId,
      String type,
      String correlationId,
      String idempotencyKey,
      JsonNode request,
      Supplier<T> action) {
    ConnectorOperation operation =
        start(instance, actorId, type, correlationId, idempotencyKey, request);
    try {
      T result = action.get();
      operation.succeed(write(result), clock.instant());
      operations.save(operation);
      return result;
    } catch (RuntimeException exception) {
      operation.fail(errorCode(exception), safeMessage(exception), clock.instant());
      operations.save(operation);
      throw normalized(exception);
    }
  }

  private ConnectorOperation start(
      ConnectorInstance instance,
      String actorId,
      String type,
      String correlationId,
      String idempotencyKey,
      JsonNode request) {
    String serialized = write(request == null ? mapper.createObjectNode() : request);
    if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
      throw ConnectorException.invalid("Connector request exceeds 1 MiB");
    }
    ConnectorOperation operation =
        new ConnectorOperation(
            UUID.randomUUID().toString(),
            ConnectorService.TENANT,
            instance.getId(),
            actorId,
            type,
            correlation(correlationId),
            idempotencyKey,
            sha256(serialized),
            clock.instant());
    return operations.save(operation);
  }

  private ConnectorConfig config(ConnectorInstance instance) {
    try {
      return new ConnectorConfig(
          mapper.readTree(instance.getConfigJson()),
          secrets.resolve(instance.getTenantId(), instance.getCredentialRef()));
    } catch (ConnectorException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Stored connector config is invalid", exception);
    }
  }

  private JsonNode delivery(WebhookDelivery value) {
    try {
      var result = mapper.createObjectNode();
      result.put("eventId", value.getEventId());
      result.put("receivedAt", value.getReceivedAt().toString());
      result.put("signatureValid", value.isSignatureValid());
      result.set("payload", mapper.readTree(value.getPayloadJson()));
      result.set("headers", mapper.readTree(value.getHeadersJson()));
      return result;
    } catch (Exception exception) {
      throw new IllegalStateException("Stored webhook delivery is invalid", exception);
    }
  }

  private ConnectorInstance active(String actorId, String connectorId) {
    ConnectorInstance instance = connectorService.get(actorId, connectorId);
    if (instance.getStatus() != ConnectorStatus.ACTIVE) {
      throw ConnectorException.conflict("Connector must be active");
    }
    return instance;
  }

  private void fail(
      ConnectorInstance instance, ConnectorOperation operation, RuntimeException exception) {
    String message = safeMessage(exception);
    instance.healthFailure(message, clock.instant());
    operation.fail(errorCode(exception), message, clock.instant());
    connectors.save(instance);
    operations.save(operation);
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Connector result is not serializable", exception);
    }
  }

  private static String correlation(String value) {
    if (value == null || value.isBlank()) {
      return UUID.randomUUID().toString();
    }
    if (value.length() > 64) {
      throw ConnectorException.invalid("Correlation identifier is too long");
    }
    return value;
  }

  private static void validIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw ConnectorException.invalid("A valid Idempotency-Key is required");
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String errorCode(RuntimeException exception) {
    return exception instanceof ConnectorAdapterException adapter
        ? adapter.getCode()
        : "CONN-A-001";
  }

  private static String safeMessage(RuntimeException exception) {
    if (exception instanceof ConnectorAdapterException) {
      return exception.getMessage();
    }
    return "Connector operation failed";
  }

  private static ConnectorException normalized(RuntimeException exception) {
    return ConnectorException.conflict(safeMessage(exception));
  }

  public record CatalogEntry(ConnectorMetadata metadata, List<ConfigField> configSchema) {
    public CatalogEntry {
      configSchema = List.copyOf(configSchema);
    }
  }
}
