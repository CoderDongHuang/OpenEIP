package com.openeip.chat.api.dto;

import com.openeip.chat.domain.entity.ChatMessage;
import java.time.Instant;

public record ChatMessageResponse(
    String messageId, long sequence, String role, String content, Instant createdAt) {
  public static ChatMessageResponse from(ChatMessage message) {
    return new ChatMessageResponse(
        message.getId(),
        message.getMessageIndex(),
        message.getRole().name().toLowerCase(java.util.Locale.ROOT),
        message.getContent(),
        message.getCreatedAt());
  }
}
