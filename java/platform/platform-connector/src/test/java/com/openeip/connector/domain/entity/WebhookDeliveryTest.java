package com.openeip.connector.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookDeliveryTest {
  @Test
  void exposesImmutableDeliveryRecordFields() {
    Instant received = Instant.parse("2026-07-25T00:00:00Z");
    WebhookDelivery delivery =
        new WebhookDelivery(
            "delivery",
            "tenant",
            "connector",
            "event",
            "{\"header\":\"value\"}",
            "{\"ok\":true}",
            true,
            received);
    assertThat(delivery.getId()).isEqualTo("delivery");
    assertThat(delivery.getTenantId()).isEqualTo("tenant");
    assertThat(delivery.getConnectorId()).isEqualTo("connector");
    assertThat(delivery.getEventId()).isEqualTo("event");
    assertThat(delivery.getHeadersJson()).contains("header");
    assertThat(delivery.getPayloadJson()).contains("ok");
    assertThat(delivery.isSignatureValid()).isTrue();
    assertThat(delivery.getReceivedAt()).isEqualTo(received);
  }
}
