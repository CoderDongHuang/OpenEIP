package com.openeip.agent.v2.domain;

import java.time.Instant;
import java.util.List;

public final class AgentPlatformModels {
  private AgentPlatformModels() {}

  public record AgentDefinition(
      String id,
      String tenantId,
      String ownerId,
      String name,
      String type,
      String description,
      String status,
      String draftJson,
      long draftRevision,
      Integer publishedVersion,
      long revision,
      Instant createdAt,
      Instant updatedAt,
      Instant archivedAt) {}

  public record AgentVersion(
      String id,
      String tenantId,
      String agentId,
      Integer version,
      String status,
      long sourceDraftRevision,
      String digest,
      String configJson,
      String evaluationRunId,
      String createdBy,
      Instant createdAt,
      String publishedBy,
      Instant publishedAt) {}

  public record ToolVersion(
      String id,
      String toolKey,
      String name,
      String description,
      String version,
      String digest,
      String riskClass,
      String idempotencyMode,
      String operationsJson,
      String inputSchemaJson,
      String outputSchemaJson,
      int maxDurationMs,
      int maxResultBytes) {}

  public record ToolGrant(
      String id,
      String agentVersionId,
      String toolVersionId,
      String operationsJson,
      String resourceSelectorJson,
      String argumentConstraintsJson,
      String approvalMode,
      Instant expiresAt,
      long revision,
      Instant createdAt,
      Instant revokedAt) {}

  public record AgentRun(
      String id,
      String agentId,
      String agentVersionId,
      int agentVersion,
      String principalId,
      String status,
      String inputDigest,
      String resourceHandlesJson,
      String budgetJson,
      String dependencyDigest,
      long currentSequence,
      long revision,
      String failureCode,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt) {}

  public record AgentRunEvent(
      String id,
      String runId,
      long sequence,
      String type,
      String payloadJson,
      Instant occurredAt) {}

  public record MemoryEntry(
      String id,
      String principalId,
      String agentId,
      String purpose,
      String sourceType,
      String sourceId,
      String sensitivity,
      String provenanceJson,
      String contentDigest,
      String vectorEntryId,
      String state,
      Instant retentionDeadline,
      long revision,
      Instant createdAt,
      Instant deletedAt) {}

  public record McpServer(
      String id,
      String ownerId,
      String name,
      String transport,
      String endpoint,
      String authType,
      String credentialRef,
      String status,
      long revision,
      Instant createdAt,
      Instant updatedAt,
      Instant disabledAt) {}

  public record McpCapability(
      String id,
      String serverId,
      String externalName,
      String type,
      String schemaDigest,
      String schemaJson,
      String status,
      Instant discoveredAt) {}

  public record EvaluationDataset(
      String id,
      String ownerId,
      String name,
      String description,
      String status,
      long revision,
      Instant createdAt,
      Instant updatedAt) {}

  public record EvaluationCase(
      String key, String agentType, String fixtureJson, String assertionsJson, String digest) {}

  public record EvaluationSuite(
      String id,
      String ownerId,
      String name,
      String status,
      long revision,
      Instant createdAt,
      String versionId,
      String datasetVersionsJson,
      String gatePolicyJson,
      String digest) {}

  public record EvaluationRun(
      String id,
      String suiteVersionId,
      String candidateAgentVersionId,
      String baselineAgentVersionId,
      int repeatCount,
      String status,
      String gateStatus,
      String environmentDigest,
      long revision,
      String createdBy,
      Instant createdAt,
      Instant completedAt,
      List<Metric> metrics,
      List<Gate> gates) {
    public EvaluationRun {
      metrics = List.copyOf(metrics);
      gates = List.copyOf(gates);
    }
  }

  public record Metric(String key, double value, int sampleCount, Double low, Double high) {}

  public record Gate(
      String key, String status, Double actual, Double threshold, String reasonCode) {}
}
