package com.openeip.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Durable idempotency record for an accepted lifecycle event. */
@Entity
@Table(name = "knowledge_processed_events")
public class ProcessedKnowledgeEvent {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "event_id", length = 36, nullable = false, updatable = false)
  private String eventId;

  @Column(name = "event_type", length = 64, nullable = false, updatable = false)
  private String eventType;

  @Column(name = "resource_key", length = 160, nullable = false, updatable = false)
  private String resourceKey;

  @Column(name = "payload_fingerprint", length = 64, nullable = false, updatable = false)
  private String payloadFingerprint;

  @Column(length = 32, nullable = false, updatable = false)
  private String outcome;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  protected ProcessedKnowledgeEvent() {}

  public ProcessedKnowledgeEvent(
      String id,
      String tenantId,
      String eventId,
      String eventType,
      String resourceKey,
      String payloadFingerprint,
      String outcome,
      Instant processedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.eventId = eventId;
    this.eventType = eventType;
    this.resourceKey = resourceKey;
    this.payloadFingerprint = payloadFingerprint;
    this.outcome = outcome;
    this.processedAt = processedAt;
  }

  public boolean matches(String type, String resource, String fingerprint) {
    return eventType.equals(type)
        && resourceKey.equals(resource)
        && payloadFingerprint.equals(fingerprint);
  }

  public String getOutcome() {
    return outcome;
  }
}
