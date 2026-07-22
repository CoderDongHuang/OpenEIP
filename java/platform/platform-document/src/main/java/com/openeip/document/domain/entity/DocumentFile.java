package com.openeip.document.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Transactional metadata for one raw uploaded object. */
@Entity
@Table(name = "document_files")
public class DocumentFile {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(name = "original_name", length = 255, nullable = false, updatable = false)
  private String originalName;

  @Column(name = "object_key", length = 255, nullable = false, unique = true, updatable = false)
  private String objectKey;

  @Column(name = "content_type", length = 127, nullable = false, updatable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(length = 64, nullable = false, updatable = false)
  private String sha256;

  @Column(length = 32, nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected DocumentFile() {}

  public DocumentFile(
      String id,
      String tenantId,
      String ownerId,
      String originalName,
      String objectKey,
      String contentType,
      long sizeBytes,
      String sha256,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.originalName = originalName;
    this.objectKey = objectKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.status = "READY";
    this.createdAt = now;
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

  public String getOriginalName() {
    return originalName;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public String getStatus() {
    return status;
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
