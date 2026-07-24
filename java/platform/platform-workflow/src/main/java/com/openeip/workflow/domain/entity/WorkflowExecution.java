package com.openeip.workflow.domain.entity;

import com.openeip.workflow.domain.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "workflow_executions")
public class WorkflowExecution {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
  private String workflowId;

  @Column(name = "workflow_version", nullable = false, updatable = false)
  private int workflowVersion;

  @Enumerated(EnumType.STRING)
  @Column(length = 24, nullable = false)
  private ExecutionStatus status;

  @Column(name = "trigger_type", length = 16, nullable = false, updatable = false)
  private String triggerType;

  @Column(name = "idempotency_key", length = 128, nullable = false, updatable = false)
  private String idempotencyKey;

  @Lob
  @Column(name = "input_json", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
  private String inputJson;

  @Column(name = "current_sequence", nullable = false)
  private long currentSequence;

  @Column(name = "failure_code", length = 16)
  private String failureCode;

  @Column(name = "resume_at")
  private Instant resumeAt;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected WorkflowExecution() {}

  public WorkflowExecution(
      String id,
      String tenantId,
      String workflowId,
      int workflowVersion,
      String triggerType,
      String idempotencyKey,
      String inputJson,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.workflowId = workflowId;
    this.workflowVersion = workflowVersion;
    this.status = ExecutionStatus.QUEUED;
    this.triggerType = triggerType;
    this.idempotencyKey = idempotencyKey;
    this.inputJson = inputJson;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public long nextSequence() {
    return ++currentSequence;
  }

  public void transition(ExecutionStatus status, Instant now) {
    this.status = status;
    this.updatedAt = now;
    if (status.terminal()) {
      this.completedAt = now;
      this.resumeAt = null;
    }
  }

  public void waitUntil(ExecutionStatus status, Instant resumeAt, Instant now) {
    this.status = status;
    this.resumeAt = resumeAt;
    this.updatedAt = now;
  }

  public void fail(String code, Instant now) {
    this.failureCode = code;
    transition(ExecutionStatus.FAILED, now);
  }

  public void resume(Instant now) {
    this.failureCode = null;
    this.resumeAt = null;
    transition(ExecutionStatus.RUNNING, now);
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public int getWorkflowVersion() {
    return workflowVersion;
  }

  public ExecutionStatus getStatus() {
    return status;
  }

  public String getTriggerType() {
    return triggerType;
  }

  public String getInputJson() {
    return inputJson;
  }

  public long getCurrentSequence() {
    return currentSequence;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public Instant getResumeAt() {
    return resumeAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
