package com.openeip.agent.application;

import com.openeip.agent.api.dto.AgentMetadataResponse;
import com.openeip.agent.api.dto.AgentToolResponse;
import com.openeip.agent.api.dto.ExecuteAgentRequest;
import com.openeip.agent.shared.exception.AgentException;
import com.openeip.knowledge.application.KnowledgeBaseService;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Validate Agent authority and resolve canonical execution context. */
@Service
public class AgentExecutionService {
  public static final String AGENT_ID = "openeip.constrained-v1";
  public static final String MVP_EXTERNAL_TENANT = "11111111-1111-4111-8111-111111111111";
  private static final Set<String> TOOLS = Set.of("document.inspect", "knowledge.search");

  private final KnowledgeBaseService knowledge;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected service is application scoped.")
  public AgentExecutionService(KnowledgeBaseService knowledge) {
    this.knowledge = knowledge;
  }

  public List<AgentMetadataResponse> catalog() {
    var inspect =
        new AgentToolResponse(
            "document.inspect",
            "Return SHA-256 and bounded text statistics",
            Map.of("type", "object", "additionalProperties", false, "required", List.of("text")));
    var search =
        new AgentToolResponse(
            "knowledge.search",
            "Search one authorized knowledge base through RAG",
            Map.of(
                "type",
                "object",
                "additionalProperties",
                false,
                "required",
                List.of("query", "topK")));
    return List.of(
        new AgentMetadataResponse(
            AGENT_ID,
            "OpenEIP Constrained Agent",
            "Bounded single-Agent runtime with explicit Document and Search tools",
            "1.0.0",
            "1.0",
            List.of(inspect, search)));
  }

  public ExecutionContext begin(
      String userId, String requestId, String agentId, ExecuteAgentRequest request) {
    validUuid(userId);
    validUuid(requestId);
    if (!AGENT_ID.equals(agentId)) {
      throw AgentException.notFound();
    }
    String input = validInput(request.input());
    Set<String> allowed = new HashSet<>(request.allowedTools());
    if (allowed.size() != request.allowedTools().size()
        || allowed.isEmpty()
        || !TOOLS.containsAll(allowed)) {
      throw AgentException.invalid("Invalid Agent tool allowlist");
    }
    int maxSteps = request.resolvedMaxSteps();
    if (maxSteps < 1 || maxSteps > 8) {
      throw AgentException.invalid("Invalid Agent step limit");
    }
    String knowledgeBaseId = request.knowledgeBaseId();
    if (knowledgeBaseId != null) {
      validUuid(knowledgeBaseId);
      try {
        knowledge.get(userId, knowledgeBaseId);
      } catch (KnowledgeException exception) {
        throw AgentException.notFound();
      }
    } else if (allowed.contains("knowledge.search")) {
      throw AgentException.invalid("Knowledge base is required");
    }
    return new ExecutionContext(
        UUID.randomUUID().toString(),
        requestId,
        agentId,
        input,
        knowledgeBaseId,
        Set.copyOf(allowed),
        maxSteps);
  }

  private static String validInput(String value) {
    if (value == null
        || value.isBlank()
        || value.length() > 4000
        || value
            .chars()
            .anyMatch(
                character ->
                    character < 32
                        && character != '\n'
                        && character != '\r'
                        && character != '\t')) {
      throw AgentException.invalid("Invalid Agent input");
    }
    return value;
  }

  private static void validUuid(String value) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw AgentException.invalid("Invalid Agent identifier");
      }
    } catch (IllegalArgumentException exception) {
      throw AgentException.invalid("Invalid Agent identifier");
    }
  }

  public record ExecutionContext(
      String executionId,
      String requestId,
      String agentId,
      String input,
      String knowledgeBaseId,
      Set<String> allowedTools,
      int maxSteps) {
    public ExecutionContext {
      allowedTools = Set.copyOf(allowedTools);
    }
  }
}
