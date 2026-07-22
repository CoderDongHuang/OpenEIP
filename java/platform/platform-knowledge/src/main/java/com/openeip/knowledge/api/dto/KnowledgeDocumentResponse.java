package com.openeip.knowledge.api.dto;

import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import java.time.Instant;

public record KnowledgeDocumentResponse(
    String documentId,
    String status,
    String failureCode,
    int retryCount,
    Instant createdAt,
    Instant updatedAt) {
  public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
    return new KnowledgeDocumentResponse(
        document.getDocumentId(),
        document.getStatus().name(),
        document.getFailureCode(),
        document.getRetryCount(),
        document.getCreatedAt(),
        document.getUpdatedAt());
  }
}
