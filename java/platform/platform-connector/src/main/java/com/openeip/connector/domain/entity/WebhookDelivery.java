package com.openeip.connector.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "connector_webhook_deliveries")
public class WebhookDelivery {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "connector_id", length = 36, nullable = false, updatable = false)
  private String connectorId;

  @Column(name = "event_id", length = 128, nullable = false, updatable = false)
  private String eventId;

  @Column(name = "headers_json", columnDefinition = "json", nullable = false, updatable = false)
  private String headersJson;

  @Column(name = "payload_json", columnDefinition = "json", nullable = false, updatable = false)
  private String payloadJson;

  @Column(name = "signature_valid", nullable = false, updatable = false)
  private boolean signatureValid;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  protected WebhookDelivery() {}

  public WebhookDelivery(
      String id,
      String tenantId,
      String connectorId,
      String eventId,
      String headersJson,
      String payloadJson,
      boolean signatureValid,
      Instant receivedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.connectorId = connectorId;
    this.eventId = eventId;
    this.headersJson = headersJson;
    this.payloadJson = payloadJson;
    this.signatureValid = signatureValid;
    this.receivedAt = receivedAt;
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getConnectorId() {
    return connectorId;
  }

  public String getEventId() {
    return eventId;
  }

  public String getHeadersJson() {
    return headersJson;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public boolean isSignatureValid() {
    return signatureValid;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
