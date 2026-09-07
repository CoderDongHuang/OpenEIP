package com.openeip.governance.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class GovernanceDtos {
  private GovernanceDtos() {}

  public record CursorPage(List<?> items, String nextCursor) {
    public CursorPage {
      items = List.copyOf(items);
    }
  }

  public record CreateModelRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(max = 128) String providerRef,
      @NotEmpty @Size(max = 32) List<@NotBlank @Size(max = 64) String> capabilities,
      @Size(max = 32) List<@NotBlank @Size(max = 64) String> routingLabels,
      @NotBlank @Size(max = 256) String secretRef) {
    public CreateModelRequest {
      capabilities = capabilities == null ? null : List.copyOf(capabilities);
      routingLabels = routingLabels == null ? null : List.copyOf(routingLabels);
    }
  }

  public record ReviewRequest(
      @NotBlank @Size(max = 16) String decision, @NotBlank @Size(max = 512) String reason) {}

  public record CreatePromptRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(max = 128) String purpose,
      @NotBlank @Size(max = 65536) String content) {}

  public record CreatePromptVersionRequest(
      @NotBlank @Size(max = 65536) String content,
      @NotBlank @Size(max = 64) String compatibilityVersion) {}

  public record EvaluateRequest(UUID suiteId, UUID baselineId) {}

  public record PublishPromptRequest(UUID versionId, UUID evaluationRunId) {}

  public record RollbackPromptRequest(UUID versionId, @NotBlank @Size(max = 512) String reason) {}

  public record CreateBudgetRequest(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000000") BigDecimal limit,
      @NotBlank String window) {}

  public record VerifyAuditRequest(java.time.Instant from, java.time.Instant to) {}
}
