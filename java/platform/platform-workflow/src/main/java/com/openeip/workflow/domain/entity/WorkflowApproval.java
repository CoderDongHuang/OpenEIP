package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_approvals")
public class WorkflowApproval {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
  private String executionId;

  @Column(name = "node_id", length = 64, nullable = false, updatable = false)
  private String nodeId;

  @Column(length = 16, nullable = false)
  private String status;

  @Lob
  @Column(name = "assignees_json", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String assigneesJson;

  @Column(name = "decision_mode", length = 8, nullable = false, updatable = false)
  private String decisionMode;

  @Column(name = "decided_by", length = 36)
  private String decidedBy;

  @Column(length = 16)
  private String decision;

  @Column(length = 1000)
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  protected WorkflowApproval() {}

  public WorkflowApproval(
      String id,
      String tenantId,
      String executionId,
      String nodeId,
      String assigneesJson,
      String decisionMode,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.executionId = executionId;
    this.nodeId = nodeId;
    this.assigneesJson = assigneesJson;
    this.decisionMode = decisionMode;
    this.status = "PENDING";
    this.createdAt = now;
  }

  public void decide(String userId, String decision, String comment, Instant now) {
    this.status = decision.equals("APPROVE") ? "APPROVED" : "REJECTED";
    this.decidedBy = userId;
    this.decision = decision;
    this.comment = comment;
    this.decidedAt = now;
  }

  public void reopen() {
    this.status = "PENDING";
    this.decidedBy = null;
    this.decision = null;
    this.comment = null;
    this.decidedAt = null;
  }

  public String getId() {
    return id;
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public String getStatus() {
    return status;
  }

  public String getAssigneesJson() {
    return assigneesJson;
  }

  public String getDecision() {
    return decision;
  }

  public String getDecisionMode() {
    return decisionMode;
  }
}
