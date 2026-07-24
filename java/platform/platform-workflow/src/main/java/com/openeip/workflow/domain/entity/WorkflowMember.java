package com.openeip.workflow.domain.entity;

import com.openeip.workflow.domain.WorkflowRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_members")
public class WorkflowMember {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
  private String workflowId;

  @Column(name = "user_id", length = 36, nullable = false, updatable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "member_role", length = 16, nullable = false)
  private WorkflowRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected WorkflowMember() {}

  public WorkflowMember(
      String id,
      String tenantId,
      String workflowId,
      String userId,
      WorkflowRole role,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.workflowId = workflowId;
    this.userId = userId;
    this.role = role;
    this.createdAt = now;
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

  public String getUserId() {
    return userId;
  }

  public WorkflowRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
