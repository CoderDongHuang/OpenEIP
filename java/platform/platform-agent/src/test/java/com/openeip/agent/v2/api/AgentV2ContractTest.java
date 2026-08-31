package com.openeip.agent.v2.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class AgentV2ContractTest {
  private static final String CANDIDATE_PATH = "/api/v2/agents/{agentId}/versions:candidate";

  @Test
  void candidateSnapshotControllerAndOpenApiOperationsStaySynchronized() throws Exception {
    var method =
        Arrays.stream(AgentDefinitionV2Controller.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals("candidate"))
            .findFirst()
            .orElseThrow();
    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).contains("/{agentId}/versions:candidate");

    JsonNode operation =
        new ObjectMapper(new YAMLFactory())
            .readTree(repositoryRoot().resolve("docs/06-api/agent-v2.openapi.yaml").toFile())
            .path("paths")
            .path(CANDIDATE_PATH)
            .path("post");
    assertThat(operation.path("operationId").asText()).isEqualTo("createAgentVersionCandidate");
    assertThat(operation.path("responses").has("201")).isTrue();
    assertThat(operation.path("parameters").toString())
        .contains("AgentId", "IdempotencyKey", "IfMatch");
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/06-api"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("Unable to locate repository root");
    }
    return current;
  }
}
