package com.openeip.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Tenant-scoped knowledge-base metadata. */
@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(length = 120, nullable = false)
  private String name;

  @Column(length = 2000, nullable = false)
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected KnowledgeBase() {}

  public KnowledgeBase(
      String id, String tenantId, String ownerId, String name, String description, Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.name = name;
    this.description = description;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String name, String description, Instant now) {
    this.name = name;
    this.description = description;
    this.updatedAt = now;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
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
