package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.agent.v2.api.AgentPlatformDtos.PageResponse;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentDefinition;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRun;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentRunEvent;
import com.openeip.agent.v2.domain.AgentPlatformModels.AgentVersion;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationSuite;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpServer;
import com.openeip.agent.v2.domain.AgentPlatformModels.MemoryEntry;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolGrant;
import com.openeip.agent.v2.domain.AgentPlatformModels.ToolVersion;
import java.util.List;

final class AgentPlatformApiSupport {
  private final ObjectMapper mapper;

  AgentPlatformApiSupport(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  PageResponse page(List<?> values) {
    return new PageResponse(values.stream().map(this::json).toList(), null);
  }

  JsonNode json(Object value) {
    JsonNode encoded = mapper.valueToTree(value);
    if (!(encoded instanceof ObjectNode result)) {
      return encoded;
    }
    if (value instanceof AgentDefinition) {
      renameJson(result, "draftJson", "draft");
      result.remove("tenantId");
    } else if (value instanceof AgentVersion) {
      renameJson(result, "configJson", "config");
      result.remove("tenantId");
    } else if (value instanceof ToolVersion) {
      renameJson(result, "operationsJson", "operations");
      renameJson(result, "inputSchemaJson", "inputSchema");
      renameJson(result, "outputSchemaJson", "outputSchema");
    } else if (value instanceof ToolGrant) {
      renameJson(result, "operationsJson", "operations");
      renameJson(result, "resourceSelectorJson", "resourceSelector");
      renameJson(result, "argumentConstraintsJson", "argumentConstraints");
    } else if (value instanceof AgentRun) {
      renameJson(result, "resourceHandlesJson", "resourceHandles");
      renameJson(result, "budgetJson", "budget");
    } else if (value instanceof AgentRunEvent) {
      renameJson(result, "payloadJson", "payload");
    } else if (value instanceof MemoryEntry) {
      renameJson(result, "provenanceJson", "provenance");
    } else if (value instanceof McpServer) {
      result.remove(List.of("ownerId", "credentialRef"));
      result.put("credentialConfigured", ((McpServer) value).credentialRef() != null);
    } else if (value instanceof EvaluationSuite) {
      renameJson(result, "datasetVersionsJson", "datasetVersionIds");
      renameJson(result, "gatePolicyJson", "gatePolicy");
    }
    return result;
  }

  private void renameJson(ObjectNode value, String source, String target) {
    JsonNode encoded = value.remove(source);
    try {
      value.set(
          target,
          encoded == null || encoded.isNull()
              ? mapper.nullNode()
              : mapper.readTree(encoded.asText()));
    } catch (Exception exception) {
      throw new IllegalStateException("Stored Agent API JSON is invalid", exception);
    }
  }
}
