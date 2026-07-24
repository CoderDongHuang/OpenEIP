package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_triggers")
public class WorkflowTrigger {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
  private String workflowId;

  @Column(name = "trigger_type", length = 16, nullable = false, updatable = false)
  private String type;

  @Column(nullable = false)
  private boolean enabled;

  @Lob
  @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
  private String configJson;

  @Column(name = "secret_hash", length = 64)
  private String secretHash;

  @Column(name = "next_fire_at")
  private Instant nextFireAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected WorkflowTrigger() {}

  public WorkflowTrigger(
      String id,
      String tenantId,
      String workflowId,
      String type,
      boolean enabled,
      String configJson,
      String secretHash,
      Instant nextFireAt,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.workflowId = workflowId;
    this.type = type;
    this.enabled = enabled;
    this.configJson = configJson;
    this.secretHash = secretHash;
    this.nextFireAt = nextFireAt;
    this.createdAt = now;
  }

  public void nextFireAt(Instant value) {
    this.nextFireAt = value;
  }

  public String getId() {
    return id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public String getType() {
    return type;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getConfigJson() {
    return configJson;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public Instant getNextFireAt() {
    return nextFireAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
