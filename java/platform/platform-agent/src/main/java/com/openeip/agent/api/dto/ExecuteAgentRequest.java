package com.openeip.agent.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Explicit bounded user execution request. */
public record ExecuteAgentRequest(
    @NotBlank @Size(max = 4000) String input,
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        String knowledgeBaseId,
    @NotEmpty @Size(max = 2)
        List<@Valid @Pattern(regexp = "^[a-z]+\\.[a-z]+$") String> allowedTools,
    @Min(1) @Max(8) Integer maxSteps) {
  public ExecuteAgentRequest {
    allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
  }

  public int resolvedMaxSteps() {
    return maxSteps == null ? 4 : maxSteps;
  }
}
