package com.openeip.knowledge.domain.entity;

import com.openeip.knowledge.domain.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One explicit knowledge-base membership grant. */
@Entity
@Table(name = "knowledge_base_members")
public class KnowledgeBaseMember {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "knowledge_base_id", length = 36, nullable = false, updatable = false)
  private String knowledgeBaseId;

  @Column(name = "user_id", length = 36, nullable = false, updatable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "member_role", length = 16, nullable = false)
  private MemberRole role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected KnowledgeBaseMember() {}

  public KnowledgeBaseMember(
      String id,
      String tenantId,
      String knowledgeBaseId,
      String userId,
      MemberRole role,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.knowledgeBaseId = knowledgeBaseId;
    this.userId = userId;
    this.role = role;
    this.createdAt = now;
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

  public String getUserId() {
    return userId;
  }

  public MemberRole getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
