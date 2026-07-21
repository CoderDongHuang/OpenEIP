package com.openeip.knowledge.domain.entity;

import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;

/** Processing state for one file associated with a knowledge base. */
@Entity
@Table(name = "knowledge_base_documents")
public class KnowledgeDocument {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "knowledge_base_id", length = 36, nullable = false, updatable = false)
  private String knowledgeBaseId;

  @Column(name = "document_id", length = 36, nullable = false, updatable = false)
  private String documentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", length = 32, nullable = false)
  private ProcessingStatus status;

  @Column(name = "failure_code", length = 64)
  private String failureCode;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected KnowledgeDocument() {}

  public KnowledgeDocument(
      String id, String tenantId, String knowledgeBaseId, String documentId, Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.knowledgeBaseId = knowledgeBaseId;
    this.documentId = documentId;
    this.status = ProcessingStatus.PENDING_PARSE;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public boolean transitionTo(ProcessingStatus target, Instant now) {
    if (status == target) return false;
    boolean allowed =
        switch (status) {
          case PENDING_PARSE ->
              target == ProcessingStatus.PARSED || target == ProcessingStatus.FAILED;
          case PARSED ->
              target == ProcessingStatus.PENDING_EMBEDDING || target == ProcessingStatus.FAILED;
          case PENDING_EMBEDDING ->
              target == ProcessingStatus.READY || target == ProcessingStatus.FAILED;
          case READY, FAILED -> false;
        };
    if (!allowed) throw KnowledgeException.invalidTransition(status, target);
    status = target;
    failureCode = null;
    updatedAt = now;
    return true;
  }

  public void fail(String code, Instant now) {
    if (code == null || !code.matches("[A-Z][A-Z0-9_.-]{0,63}")) {
      throw KnowledgeException.invalid("Invalid failure code");
    }
    if (status == ProcessingStatus.READY) {
      throw KnowledgeException.invalidTransition(status, ProcessingStatus.FAILED);
    }
    status = ProcessingStatus.FAILED;
    failureCode = code;
    retryCount = Math.min(3, retryCount + 1);
    updatedAt = now;
  }

  public boolean isAtOrBeyondParsed() {
    return Set.of(
            ProcessingStatus.PARSED, ProcessingStatus.PENDING_EMBEDDING, ProcessingStatus.READY)
        .contains(status);
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public String getDocumentId() {
    return documentId;
  }

  public ProcessingStatus getStatus() {
    return status;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
