package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_approval_decisions")
public class WorkflowApprovalDecision {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "approval_id", length = 36, nullable = false, updatable = false)
  private String approvalId;

  @Column(name = "assignee_id", length = 36, nullable = false, updatable = false)
  private String assigneeId;

  @Column(length = 16, nullable = false, updatable = false)
  private String decision;

  @Column(length = 1000, nullable = false, updatable = false)
  private String comment;

  @Column(name = "idempotency_key", length = 128, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  protected WorkflowApprovalDecision() {}

  public WorkflowApprovalDecision(
      String id,
      String tenantId,
      String approvalId,
      String assigneeId,
      String decision,
      String comment,
      String idempotencyKey,
      Instant decidedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.approvalId = approvalId;
    this.assigneeId = assigneeId;
    this.decision = decision;
    this.comment = comment;
    this.idempotencyKey = idempotencyKey;
    this.decidedAt = decidedAt;
  }
}
