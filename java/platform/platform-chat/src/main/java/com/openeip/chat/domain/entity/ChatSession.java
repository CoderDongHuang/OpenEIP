package com.openeip.chat.domain.entity;

import com.openeip.chat.shared.exception.ChatException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** User-owned chat session with a database-visible single-stream lease. */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(name = "knowledge_base_id", length = 36, nullable = false, updatable = false)
  private String knowledgeBaseId;

  @Column(length = 120, nullable = false)
  private String title;

  @Column(name = "next_message_index", nullable = false)
  private long nextMessageIndex;

  @Column(name = "active_request_id", length = 36)
  private String activeRequestId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChatSession() {}

  public ChatSession(
      String id,
      String tenantId,
      String ownerId,
      String knowledgeBaseId,
      String title,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.knowledgeBaseId = knowledgeBaseId;
    this.title = title;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public long begin(String requestId, Instant now) {
    if (activeRequestId != null) {
      throw ChatException.conflict("A message is already streaming for this session");
    }
    activeRequestId = requestId;
    updatedAt = now;
    return nextMessageIndex++;
  }

  public long complete(String requestId, Instant now) {
    requireActive(requestId);
    activeRequestId = null;
    updatedAt = now;
    return nextMessageIndex++;
  }

  public void cancel(String requestId, Instant now) {
    if (requestId.equals(activeRequestId)) {
      activeRequestId = null;
      updatedAt = now;
    }
  }

  private void requireActive(String requestId) {
    if (!requestId.equals(activeRequestId)) {
      throw ChatException.conflict("Chat stream is no longer active");
    }
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

  public String getKnowledgeBaseId() {
    return knowledgeBaseId;
  }

  public String getTitle() {
    return title;
  }

  public long getNextMessageIndex() {
    return nextMessageIndex;
  }

  public String getActiveRequestId() {
    return activeRequestId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
