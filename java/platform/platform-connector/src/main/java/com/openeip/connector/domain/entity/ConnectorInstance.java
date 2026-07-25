package com.openeip.connector.domain.entity;

import com.openeip.connector.domain.ConnectorStatus;
import com.openeip.connector.domain.ConnectorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "connector_instances")
public class ConnectorInstance {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
  private String ownerId;

  @Column(length = 120, nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "connector_type", length = 32, nullable = false, updatable = false)
  private ConnectorType type;

  @Enumerated(EnumType.STRING)
  @Column(length = 16, nullable = false)
  private ConnectorStatus status;

  @Column(name = "config_json", columnDefinition = "json", nullable = false)
  private String configJson;

  @Column(name = "credential_ref", length = 255)
  private String credentialRef;

  @Column(name = "last_health_at")
  private Instant lastHealthAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected ConnectorInstance() {}

  public ConnectorInstance(
      String id,
      String tenantId,
      String ownerId,
      String name,
      ConnectorType type,
      String configJson,
      String credentialRef,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
    this.status = ConnectorStatus.PAUSED;
    this.configJson = configJson;
    this.credentialRef = credentialRef;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String name, String configJson, String credentialRef, Instant now) {
    this.name = name;
    this.configJson = configJson;
    this.credentialRef = credentialRef;
    this.updatedAt = now;
  }

  public void activate(Instant now) {
    this.status = ConnectorStatus.ACTIVE;
    this.lastError = null;
    this.updatedAt = now;
  }

  public void pause(Instant now) {
    this.status = ConnectorStatus.PAUSED;
    this.updatedAt = now;
  }

  public void healthFailure(String message, Instant now) {
    this.status = ConnectorStatus.ERROR;
    this.lastError = message;
    this.lastHealthAt = now;
    this.updatedAt = now;
  }

  public void healthSuccess(Instant now) {
    this.status = ConnectorStatus.ACTIVE;
    this.lastError = null;
    this.lastHealthAt = now;
    this.updatedAt = now;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
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

  public String getName() {
    return name;
  }

  public ConnectorType getType() {
    return type;
  }

  public ConnectorStatus getStatus() {
    return status;
  }

  public String getConfigJson() {
    return configJson;
  }

  public String getCredentialRef() {
    return credentialRef;
  }

  public Instant getLastHealthAt() {
    return lastHealthAt;
  }

  public String getLastError() {
    return lastError;
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
