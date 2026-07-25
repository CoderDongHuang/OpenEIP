package com.openeip.connector.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.adapter.webhook.WebhookConnector;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.entity.WebhookDelivery;
import com.openeip.connector.domain.repository.WebhookDeliveryRepository;
import com.openeip.connector.shared.ConnectorException;
import com.openeip.connector.spi.SecretResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WebhookInboundService {
  private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;
  private final ConnectorService connectors;
  private final WebhookDeliveryRepository deliveries;
  private final SecretResolver secrets;
  private final ObjectMapper mapper;
  private final Clock clock;

  @Autowired
  public WebhookInboundService(
      ConnectorService connectors,
      WebhookDeliveryRepository deliveries,
      SecretResolver secrets,
      ObjectMapper mapper) {
    this(connectors, deliveries, secrets, mapper, Clock.systemUTC());
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application scoped.")
  WebhookInboundService(
      ConnectorService connectors,
      WebhookDeliveryRepository deliveries,
      SecretResolver secrets,
      ObjectMapper mapper,
      Clock clock) {
    this.connectors = connectors;
    this.deliveries = deliveries;
    this.secrets = secrets;
    this.mapper = mapper;
    this.clock = clock;
  }

  public ReceiveResult receive(
      String connectorId,
      String eventId,
      String timestamp,
      String signature,
      Map<String, String> headers,
      String rawPayload) {
    ConnectorInstance connector = connectors.find(connectorId);
    if (connector.getType() != ConnectorType.WEBHOOK || connector.getDeletedAt() != null) {
      throw ConnectorException.notFound();
    }
    if (connector.getStatus() != com.openeip.connector.domain.ConnectorStatus.ACTIVE) {
      throw ConnectorException.conflict("Webhook connector must be active");
    }
    String validEvent =
        eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
    if (validEvent.length() > 128 || !validEvent.matches("[A-Za-z0-9._:-]+")) {
      throw ConnectorException.invalid("Invalid webhook event id");
    }
    long epoch = parseTimestamp(timestamp);
    if (Math.abs(clock.instant().getEpochSecond() - epoch) > TIMESTAMP_TOLERANCE_SECONDS) {
      throw ConnectorException.unauthorized();
    }
    String expected =
        "v1=" + WebhookConnector.sign(secret(connector), timestamp + "." + rawPayload);
    if (signature == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8))) {
      throw ConnectorException.unauthorized();
    }
    var existing =
        deliveries.findByTenantIdAndConnectorIdAndEventId(
            ConnectorService.TENANT, connectorId, validEvent);
    if (existing.isPresent()) {
      return new ReceiveResult(existing.get().getId(), true);
    }
    JsonNode payload = parsePayload(rawPayload);
    try {
      String headersJson = mapper.writeValueAsString(new HashMap<>(headers));
      String payloadJson = mapper.writeValueAsString(payload);
      WebhookDelivery delivery =
          deliveries.save(
              new WebhookDelivery(
                  UUID.randomUUID().toString(),
                  ConnectorService.TENANT,
                  connectorId,
                  validEvent,
                  headersJson,
                  payloadJson,
                  true,
                  clock.instant()));
      return new ReceiveResult(delivery.getId(), false);
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      return deliveries
          .findByTenantIdAndConnectorIdAndEventId(ConnectorService.TENANT, connectorId, validEvent)
          .map(value -> new ReceiveResult(value.getId(), true))
          .orElseThrow(() -> ConnectorException.conflict("Webhook event collision"));
    } catch (Exception exception) {
      throw ConnectorException.invalid("Webhook payload is invalid");
    }
  }

  public java.util.List<WebhookDelivery> recent(String connectorId, int limit) {
    return deliveries.findByTenantIdAndConnectorIdOrderByReceivedAtDesc(
        ConnectorService.TENANT,
        connectorId,
        org.springframework.data.domain.PageRequest.of(0, Math.min(1000, Math.max(1, limit))));
  }

  private String secret(ConnectorInstance connector) {
    String value =
        secrets
            .resolve(connector.getTenantId(), connector.getCredentialRef())
            .getOrDefault("signingSecret", "");
    if (value.isBlank()) {
      throw ConnectorException.unauthorized();
    }
    return value;
  }

  private static long parseTimestamp(String value) {
    try {
      long result = Long.parseLong(value);
      if (result < 1) {
        throw new NumberFormatException();
      }
      return result;
    } catch (Exception exception) {
      throw ConnectorException.unauthorized();
    }
  }

  private JsonNode parsePayload(String raw) {
    try {
      return mapper.readTree(raw);
    } catch (Exception exception) {
      throw ConnectorException.invalid("Webhook payload must be JSON");
    }
  }

  public record ReceiveResult(String deliveryId, boolean duplicate) {}
}
