package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_outbox")
public class WorkflowOutbox {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "event_id", length = 36, nullable = false, updatable = false)
  private String eventId;

  @Column(name = "aggregate_id", length = 36, nullable = false, updatable = false)
  private String aggregateId;

  @Column(name = "event_type", length = 80, nullable = false, updatable = false)
  private String eventType;

  @Lob
  @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String payloadJson;

  @Column(length = 16, nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  protected WorkflowOutbox() {}

  public WorkflowOutbox(
      String id,
      String tenantId,
      String eventId,
      String aggregateId,
      String eventType,
      String payloadJson,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.eventId = eventId;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payloadJson = payloadJson;
    this.status = "PENDING";
    this.createdAt = now;
  }

  public void delivered(Instant now) {
    this.status = "DELIVERED";
    this.deliveredAt = now;
  }

  public String getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayloadJson() {
    return payloadJson;
  }
}
