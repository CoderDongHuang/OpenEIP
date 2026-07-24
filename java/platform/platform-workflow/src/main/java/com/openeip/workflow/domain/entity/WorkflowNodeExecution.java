package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_node_executions")
public class WorkflowNodeExecution {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
  private String executionId;

  @Column(name = "node_id", length = 64, nullable = false, updatable = false)
  private String nodeId;

  @Column(name = "iteration_number", nullable = false, updatable = false)
  private int iteration;

  @Column(name = "attempt_number", nullable = false, updatable = false)
  private int attempt;

  @Column(length = 16, nullable = false)
  private String status;

  @Column(name = "invocation_id", length = 96, nullable = false, updatable = false)
  private String invocationId;

  @Column(name = "failure_code", length = 16)
  private String failureCode;

  @Lob
  @Column(name = "output_json", columnDefinition = "LONGTEXT")
  private String outputJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WorkflowNodeExecution() {}

  public WorkflowNodeExecution(
      String id,
      String tenantId,
      String executionId,
      String nodeId,
      int attempt,
      String invocationId,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.executionId = executionId;
    this.nodeId = nodeId;
    this.attempt = attempt;
    this.invocationId = invocationId;
    this.status = "PENDING";
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void status(String value, Instant now) {
    this.status = value;
    this.updatedAt = now;
  }

  public void succeed(String outputJson, Instant now) {
    this.outputJson = outputJson;
    status("SUCCEEDED", now);
  }

  public void fail(String code, Instant now) {
    this.failureCode = code;
    status("FAILED", now);
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public int getAttempt() {
    return attempt;
  }

  public String getStatus() {
    return status;
  }

  public String getInvocationId() {
    return invocationId;
  }

  public String getOutputJson() {
    return outputJson;
  }
}
