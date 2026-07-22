package com.openeip.knowledge.api.dto;

import com.openeip.knowledge.application.KnowledgeIngestionService.IngestionResult;
import java.time.Instant;

/** Public bounded processing outcome without document text, vectors, or upstream details. */
public record KnowledgeProcessingResponse(
    String documentId,
    String status,
    String sourceType,
    int chunkCount,
    int vectorCount,
    Instant updatedAt) {
  public static KnowledgeProcessingResponse from(IngestionResult result) {
    return new KnowledgeProcessingResponse(
        result.document().getDocumentId(),
        result.document().getStatus().name(),
        result.sourceType(),
        result.chunkCount(),
        result.vectorCount(),
        result.document().getUpdatedAt());
  }
}
