package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflow_versions")
public class WorkflowVersion {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
  private String workflowId;

  @Column(name = "version_number", nullable = false, updatable = false)
  private int version;

  @Column(name = "graph_sha256", length = 64, nullable = false, updatable = false)
  private String graphSha256;

  @Lob
  @Column(name = "graph_json", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
  private String graphJson;

  @Column(name = "published_by", length = 36, nullable = false, updatable = false)
  private String publishedBy;

  @Column(name = "published_at", nullable = false, updatable = false)
  private Instant publishedAt;

  protected WorkflowVersion() {}

  public WorkflowVersion(
      String id,
      String tenantId,
      String workflowId,
      int version,
      String graphSha256,
      String graphJson,
      String publishedBy,
      Instant publishedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.workflowId = workflowId;
    this.version = version;
    this.graphSha256 = graphSha256;
    this.graphJson = graphJson;
    this.publishedBy = publishedBy;
    this.publishedAt = publishedAt;
  }

  public String getId() {
    return id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public int getVersion() {
    return version;
  }

  public String getGraphSha256() {
    return graphSha256;
  }

  public String getGraphJson() {
    return graphJson;
  }

  public String getPublishedBy() {
    return publishedBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
