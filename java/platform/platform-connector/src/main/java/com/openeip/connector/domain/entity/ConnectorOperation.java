package com.openeip.connector.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "connector_operations")
public class ConnectorOperation {
  @Id private String id;

  @Column(name = "tenant_id", length = 64, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "connector_id", length = 36, nullable = false, updatable = false)
  private String connectorId;

  @Column(name = "actor_id", length = 36, nullable = false, updatable = false)
  private String actorId;

  @Column(name = "operation_type", length = 24, nullable = false, updatable = false)
  private String operationType;

  @Column(length = 16, nullable = false)
  private String status;

  @Column(name = "correlation_id", length = 64, nullable = false, updatable = false)
  private String correlationId;

  @Column(name = "idempotency_key", length = 128, updatable = false)
  private String idempotencyKey;

  @Column(name = "request_fingerprint", length = 64, nullable = false, updatable = false)
  private String requestFingerprint;

  @Column(name = "result_json", columnDefinition = "json")
  private String resultJson;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected ConnectorOperation() {}

  public ConnectorOperation(
      String id,
      String tenantId,
      String connectorId,
      String actorId,
      String operationType,
      String correlationId,
      String idempotencyKey,
      String requestFingerprint,
      Instant startedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.connectorId = connectorId;
    this.actorId = actorId;
    this.operationType = operationType;
    this.status = "RUNNING";
    this.correlationId = correlationId;
    this.idempotencyKey = idempotencyKey;
    this.requestFingerprint = requestFingerprint;
    this.startedAt = startedAt;
  }

  public void succeed(String resultJson, Instant now) {
    this.status = "SUCCEEDED";
    this.resultJson = resultJson;
    this.completedAt = now;
  }

  public void fail(String code, String message, Instant now) {
    this.status = "FAILED";
    this.errorCode = code;
    this.errorMessage =
        message == null ? null : message.substring(0, Math.min(500, message.length()));
    this.completedAt = now;
  }

  public String getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getConnectorId() {
    return connectorId;
  }

  public String getActorId() {
    return actorId;
  }

  public String getOperationType() {
    return operationType;
  }

  public String getStatus() {
    return status;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public String getResultJson() {
    return resultJson;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
