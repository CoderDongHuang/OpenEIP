package com.openeip.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.workflow.application.WorkflowService.WorkflowAccess;
import com.openeip.workflow.domain.entity.WorkflowEvent;
import com.openeip.workflow.domain.entity.WorkflowExecution;
import com.openeip.workflow.domain.entity.WorkflowTrigger;
import com.openeip.workflow.domain.entity.WorkflowVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class WorkflowDtos {
  private WorkflowDtos() {}

  public record CreateWorkflowRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 2000) String description) {}

  public record UpdateWorkflowRequest(
      @NotBlank @Size(max = 120) String name,
      @NotNull @Size(max = 2000) String description,
      @NotNull JsonNode graph) {}

  public record TriggerExecutionRequest(JsonNode input) {}

  public record CreateTriggerRequest(
      @NotBlank @Pattern(regexp = "WEBHOOK|CRON|EVENT") String type,
      boolean enabled,
      @NotNull JsonNode config) {}

  public record ApprovalDecisionRequest(
      @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
      @Size(max = 1000) String comment) {}

  public record WorkflowResponse(
      String id,
      String name,
      String description,
      String role,
      String status,
      long draftRevision,
      Integer publishedVersion,
      JsonNode graph,
      Instant createdAt,
      Instant updatedAt) {
    public static WorkflowResponse from(WorkflowAccess access, ObjectMapper mapper) {
      var workflow = access.workflow();
      return new WorkflowResponse(
          workflow.getId(),
          workflow.getName(),
          workflow.getDescription(),
          access.role().name(),
          workflow.getStatus(),
          workflow.getDraftRevision(),
          workflow.getPublishedVersion(),
          read(mapper, workflow.getGraphJson()),
          workflow.getCreatedAt(),
          workflow.getUpdatedAt());
    }
  }

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "The compact constructor stores an immutable defensive copy.")
  public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {
    public PageResponse {
      items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
      return new PageResponse<>(
          page.getContent(),
          page.getNumber() + 1,
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }

  public record VersionResponse(
      String workflowId,
      int version,
      String graphSha256,
      JsonNode graph,
      String publishedBy,
      Instant publishedAt) {
    public static VersionResponse from(WorkflowVersion value, ObjectMapper mapper) {
      return new VersionResponse(
          value.getWorkflowId(),
          value.getVersion(),
          value.getGraphSha256(),
          read(mapper, value.getGraphJson()),
          value.getPublishedBy(),
          value.getPublishedAt());
    }
  }

  public record ExecutionResponse(
      String id,
      String workflowId,
      int workflowVersion,
      String status,
      String triggerType,
      long currentSequence,
      String failureCode,
      Instant createdAt,
      Instant updatedAt,
      Instant completedAt) {
    public static ExecutionResponse from(WorkflowExecution value) {
      return new ExecutionResponse(
          value.getId(),
          value.getWorkflowId(),
          value.getWorkflowVersion(),
          value.getStatus().name(),
          value.getTriggerType(),
          value.getCurrentSequence(),
          value.getFailureCode(),
          value.getCreatedAt(),
          value.getUpdatedAt(),
          value.getCompletedAt());
    }
  }

  public record EventResponse(
      String executionId,
      long sequence,
      String type,
      String nodeId,
      Instant occurredAt,
      JsonNode data) {
    public static EventResponse from(WorkflowEvent value, ObjectMapper mapper) {
      return new EventResponse(
          value.getExecutionId(),
          value.getSequence(),
          value.getType(),
          value.getNodeId(),
          value.getOccurredAt(),
          read(mapper, value.getDataJson()));
    }
  }

  public record TriggerResponse(
      String id,
      String workflowId,
      String type,
      boolean enabled,
      JsonNode config,
      String secret,
      Instant createdAt) {
    public static TriggerResponse from(WorkflowTrigger value, String secret, ObjectMapper mapper) {
      return new TriggerResponse(
          value.getId(),
          value.getWorkflowId(),
          value.getType(),
          value.isEnabled(),
          read(mapper, value.getConfigJson()),
          secret,
          value.getCreatedAt());
    }
  }

  static JsonNode read(ObjectMapper mapper, String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored workflow JSON is invalid", exception);
    }
  }
}
