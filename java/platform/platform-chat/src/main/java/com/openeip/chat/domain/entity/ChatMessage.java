package com.openeip.chat.domain.entity;

import com.openeip.chat.domain.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Immutable complete user or assistant message. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "session_id", length = 36, nullable = false, updatable = false)
  private String sessionId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(name = "message_index", nullable = false, updatable = false)
  private long messageIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "message_role", length = 16, nullable = false, updatable = false)
  private MessageRole role;

  @Column(length = 8000, nullable = false, updatable = false)
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ChatMessage() {}

  public ChatMessage(
      String id,
      String tenantId,
      String sessionId,
      String ownerId,
      long messageIndex,
      MessageRole role,
      String content,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.sessionId = sessionId;
    this.ownerId = ownerId;
    this.messageIndex = messageIndex;
    this.role = role;
    this.content = content;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public long getMessageIndex() {
    return messageIndex;
  }

  public MessageRole getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
