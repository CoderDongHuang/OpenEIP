package com.openeip.agent.api.dto;

import java.util.List;

/** Public Agent metadata and immutable tool declarations. */
public record AgentMetadataResponse(
    String agentId,
    String name,
    String description,
    String version,
    String spiVersion,
    List<AgentToolResponse> tools) {
  public AgentMetadataResponse {
    tools = List.copyOf(tools);
  }
}
