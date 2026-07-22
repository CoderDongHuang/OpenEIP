package com.openeip.chat.api.dto;

import com.openeip.chat.domain.entity.ChatSession;
import java.time.Instant;

public record ChatSessionResponse(
    String sessionId, String knowledgeBaseId, String title, Instant createdAt, Instant updatedAt) {
  public static ChatSessionResponse from(ChatSession session) {
    return new ChatSessionResponse(
        session.getId(),
        session.getKnowledgeBaseId(),
        session.getTitle(),
        session.getCreatedAt(),
        session.getUpdatedAt());
  }
}
