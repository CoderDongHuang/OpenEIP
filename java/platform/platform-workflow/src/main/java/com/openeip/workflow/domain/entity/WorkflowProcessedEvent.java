package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_processed_events")
public class WorkflowProcessedEvent {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "event_id", length = 36, nullable = false, updatable = false)
  private String eventId;

  @Column(name = "event_type", length = 80, nullable = false, updatable = false)
  private String eventType;

  @Column(name = "payload_fingerprint", length = 64, nullable = false, updatable = false)
  private String payloadFingerprint;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  protected WorkflowProcessedEvent() {}

  public WorkflowProcessedEvent(
      String id,
      String tenantId,
      String eventId,
      String eventType,
      String payloadFingerprint,
      Instant processedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.eventId = eventId;
    this.eventType = eventType;
    this.payloadFingerprint = payloadFingerprint;
    this.processedAt = processedAt;
  }

  public String getPayloadFingerprint() {
    return payloadFingerprint;
  }
}
