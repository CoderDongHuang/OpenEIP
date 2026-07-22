package com.openeip.agent.api.dto;

import java.util.Map;

/** Immutable public tool definition. */
public record AgentToolResponse(String name, String description, Map<String, Object> inputSchema) {
  public AgentToolResponse {
    inputSchema = Map.copyOf(inputSchema);
  }
}
