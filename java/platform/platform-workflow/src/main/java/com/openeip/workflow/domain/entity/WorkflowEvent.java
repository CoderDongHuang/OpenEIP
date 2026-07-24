package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_events")
public class WorkflowEvent {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
  private String executionId;

  @Column(name = "sequence_number", nullable = false, updatable = false)
  private long sequence;

  @Column(name = "event_type", length = 80, nullable = false, updatable = false)
  private String type;

  @Column(name = "node_id", length = 64)
  private String nodeId;

  @Lob
  @Column(name = "data_json", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String dataJson;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected WorkflowEvent() {}

  public WorkflowEvent(
      String id,
      String tenantId,
      String executionId,
      long sequence,
      String type,
      String nodeId,
      String dataJson,
      Instant occurredAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.executionId = executionId;
    this.sequence = sequence;
    this.type = type;
    this.nodeId = nodeId;
    this.dataJson = dataJson;
    this.occurredAt = occurredAt;
  }

  public String getId() {
    return id;
  }

  public String getExecutionId() {
    return executionId;
  }

  public long getSequence() {
    return sequence;
  }

  public String getType() {
    return type;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getDataJson() {
    return dataJson;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
