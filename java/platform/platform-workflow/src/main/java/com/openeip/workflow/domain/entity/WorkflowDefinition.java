package com.openeip.workflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(length = 120, nullable = false)
  private String name;

  @Column(length = 2000, nullable = false)
  private String description;

  @Column(length = 16, nullable = false)
  private String status;

  @Column(name = "draft_revision", nullable = false)
  private long draftRevision;

  @Column(name = "published_version")
  private Integer publishedVersion;

  @Lob
  @Column(name = "draft_graph_json", nullable = false, columnDefinition = "LONGTEXT")
  private String graphJson;

  @Version
  @Column(name = "lock_version", nullable = false)
  private long lockVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected WorkflowDefinition() {}

  public WorkflowDefinition(
      String id,
      String tenantId,
      String ownerId,
      String name,
      String description,
      String graphJson,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.name = name;
    this.description = description;
    this.status = "DRAFT";
    this.graphJson = graphJson;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void updateDraft(String name, String description, String graphJson, Instant now) {
    this.name = name;
    this.description = description;
    this.graphJson = graphJson;
    this.status = publishedVersion == null ? "DRAFT" : "PUBLISHED";
    this.draftRevision++;
    this.updatedAt = now;
  }

  public void publish(int version, Instant now) {
    this.publishedVersion = version;
    this.status = "PUBLISHED";
    this.updatedAt = now;
  }

  public void restore(String graphJson, Instant now) {
    this.graphJson = graphJson;
    this.draftRevision++;
    this.updatedAt = now;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
    this.status = "ARCHIVED";
    this.updatedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }

  public long getDraftRevision() {
    return draftRevision;
  }

  public Integer getPublishedVersion() {
    return publishedVersion;
  }

  public String getGraphJson() {
    return graphJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
