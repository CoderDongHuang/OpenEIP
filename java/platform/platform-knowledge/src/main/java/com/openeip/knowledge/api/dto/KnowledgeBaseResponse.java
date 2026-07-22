package com.openeip.knowledge.api.dto;

import com.openeip.knowledge.application.KnowledgeBaseService.BaseAccess;
import java.time.Instant;

public record KnowledgeBaseResponse(
    String id, String name, String description, String role, Instant createdAt, Instant updatedAt) {
  public static KnowledgeBaseResponse from(BaseAccess access) {
    var base = access.base();
    return new KnowledgeBaseResponse(
        base.getId(),
        base.getName(),
        base.getDescription(),
        access.role().name(),
        base.getCreatedAt(),
        base.getUpdatedAt());
  }
}
