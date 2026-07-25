package com.openeip.connector.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ConnectorRuntimeDtos {
  private ConnectorRuntimeDtos() {}

  public record ReadRequest(
      @NotBlank String resource, @NotNull JsonNode query, @Min(1) @Max(1000) int limit) {}

  public record WriteRequest(
      @NotBlank String resource, @NotBlank String operation, @NotNull JsonNode data) {}
}
