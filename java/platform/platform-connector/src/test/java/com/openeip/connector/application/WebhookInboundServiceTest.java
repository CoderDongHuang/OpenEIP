package com.openeip.connector.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.adapter.webhook.WebhookConnector;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.domain.entity.ConnectorInstance;
import com.openeip.connector.domain.entity.WebhookDelivery;
import com.openeip.connector.domain.repository.WebhookDeliveryRepository;
import com.openeip.connector.shared.ConnectorException;
import com.openeip.connector.spi.SecretResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookInboundServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
  private static final String CONNECTOR_ID = UUID.randomUUID().toString();
  private static final String SECRET = "signing-secret";

  @Mock private ConnectorService connectors;
  @Mock private WebhookDeliveryRepository deliveries;
  @Mock private SecretResolver secrets;
  private WebhookInboundService service;
  private ConnectorInstance connector;

  @BeforeEach
  void setUp() {
    connector =
        new ConnectorInstance(
            CONNECTOR_ID,
            ConnectorService.TENANT,
            "owner",
            "incoming",
            ConnectorType.WEBHOOK,
            "{\"endpoint\":\"https://example.test/hook\"}",
            "secret://env/HOOK",
            NOW);
    connector.activate(NOW);
    lenient().when(connectors.find(CONNECTOR_ID)).thenReturn(connector);
    lenient()
        .when(secrets.resolve(ConnectorService.TENANT, "secret://env/HOOK"))
        .thenReturn(Map.of("signingSecret", SECRET));
    lenient()
        .when(deliveries.save(any(WebhookDelivery.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new WebhookInboundService(
            connectors, deliveries, secrets, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void acceptsValidPayloadAndIsIdempotent() {
    String timestamp = Long.toString(NOW.getEpochSecond());
    String payload = "{\"event\":\"created\"}";
    String signature = "v1=" + WebhookConnector.sign(SECRET, timestamp + "." + payload);

    WebhookInboundService.ReceiveResult first =
        service.receive(
            CONNECTOR_ID, "event-1", timestamp, signature, Map.of("X-Test", "yes"), payload);

    assertThat(first.duplicate()).isFalse();
    when(deliveries.findByTenantIdAndConnectorIdAndEventId(
            ConnectorService.TENANT, CONNECTOR_ID, "event-1"))
        .thenReturn(
            Optional.of(
                new WebhookDelivery(
                    first.deliveryId(),
                    ConnectorService.TENANT,
                    CONNECTOR_ID,
                    "event-1",
                    "{}",
                    payload,
                    true,
                    NOW)));
    WebhookInboundService.ReceiveResult duplicate =
        service.receive(CONNECTOR_ID, "event-1", timestamp, signature, Map.of(), payload);
    assertThat(duplicate)
        .isEqualTo(new WebhookInboundService.ReceiveResult(first.deliveryId(), true));
  }

  @Test
  void rejectsExpiredAndInvalidSignatures() {
    String payload = "{}";
    String timestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());
    assertThatThrownBy(
            () -> service.receive(CONNECTOR_ID, "expired", timestamp, "v1=bad", Map.of(), payload))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("signature");

    String current = Long.toString(NOW.getEpochSecond());
    assertThatThrownBy(
            () -> service.receive(CONNECTOR_ID, "bad", current, "v1=bad", Map.of(), payload))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("signature");
  }

  @Test
  void rejectsMalformedPayloadAndMissingSecret() {
    String timestamp = Long.toString(NOW.getEpochSecond());
    String invalid = "not-json";
    String signature = "v1=" + WebhookConnector.sign(SECRET, timestamp + "." + invalid);
    assertThatThrownBy(
            () -> service.receive(CONNECTOR_ID, "invalid", timestamp, signature, Map.of(), invalid))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("JSON");

    when(secrets.resolve(ConnectorService.TENANT, "secret://env/HOOK")).thenReturn(Map.of());
    assertThatThrownBy(
            () -> service.receive(CONNECTOR_ID, "missing", timestamp, signature, Map.of(), "{}"))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("signature");
  }
}
