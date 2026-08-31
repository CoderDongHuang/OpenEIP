package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AgentPlatformDtos {
  private AgentPlatformDtos() {}

  public record CreateAgentRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 24) String type,
      @Size(max = 1000) String description) {}

  public record PublishAgentRequest(@NotBlank String evaluationRunId) {}

  public record CreateRunRequest(
      @Min(1) int agentVersion,
      @NotBlank @Size(max = 32000) String input,
      @Size(max = 64) List<String> resourceHandles,
      String sessionId,
      JsonNode budget) {
    public CreateRunRequest {
      resourceHandles = resourceHandles == null ? null : List.copyOf(resourceHandles);
    }
  }

  public record DecideApprovalRequest(@NotBlank String decision, @Size(max = 500) String reason) {}

  public record CreateGrantRequest(
      @NotBlank String agentVersionId,
      @NotBlank String toolVersionId,
      @NotNull JsonNode operations,
      @NotNull JsonNode resourceSelector,
      JsonNode argumentConstraints,
      @NotBlank String approvalMode,
      String expiresAt) {}

  public record ExportMemoryRequest(@Size(max = 64) String purpose, boolean includeContent) {}

  public record CreateMcpServerRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank String transport,
      @NotBlank @Size(max = 2048) String endpoint,
      @NotBlank String authType,
      String credentialRef) {}

  public record MapMcpToolRequest(@NotBlank String toolVersionId) {}

  public record CreateDatasetRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 1000) String description) {}

  public record CreateSuiteRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull JsonNode datasetVersionIds,
      JsonNode gatePolicy) {}

  public record CreateEvaluationRunRequest(
      @NotBlank String suiteVersionId,
      @NotBlank String candidateAgentVersionId,
      @NotBlank String baselineAgentVersionId,
      @Min(1) @Max(10) Integer repeatCount) {}

  public record PageResponse(List<JsonNode> items, String nextCursor) {
    public PageResponse {
      items = List.copyOf(items);
    }
  }
}
