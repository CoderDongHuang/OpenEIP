package com.openeip.agent.v2.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpCapability;
import com.openeip.agent.v2.domain.AgentPlatformModels.McpServer;
import com.openeip.agent.v2.shared.AgentPlatformException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "The application-scoped Jackson mapper is intentionally injected and shared.")
public class McpGatewayClient {
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final String pythonUrl;
  private final String internalToken;

  public McpGatewayClient(
      ObjectMapper mapper,
      @Value("${openeip.agent.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.agent.internal-token:}") String internalToken) {
    this.mapper = mapper;
    this.pythonUrl = pythonUrl;
    this.internalToken = internalToken;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public GatewayResult discover(McpServer server) {
    if (internalToken.isBlank()) {
      throw AgentPlatformException.upstream();
    }
    try {
      Map<String, Object> registration = new java.util.LinkedHashMap<>();
      registration.put("serverId", server.id());
      registration.put("transport", server.transport());
      registration.put("endpoint", server.endpoint());
      registration.put("authType", server.authType());
      if (server.credentialRef() != null) {
        registration.put("credentialRef", server.credentialRef());
      }
      String body = mapper.writeValueAsString(registration);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(pythonUrl + "/api/v2/internal/mcp:discover"))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .header("X-OpenEIP-Internal-Token", internalToken)
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200 || response.body().length() > 1_048_576) {
        throw AgentPlatformException.upstream();
      }
      JsonNode root = mapper.readTree(response.body());
      if (!root.path("capabilities").isArray() || root.path("capabilities").size() > 128) {
        throw AgentPlatformException.upstream();
      }
      List<McpCapability> values =
          java.util.stream.StreamSupport.stream(root.path("capabilities").spliterator(), false)
              .map(
                  value -> {
                    String schema = value.path("schema").toString();
                    return new McpCapability(
                        UUID.randomUUID().toString(),
                        server.id(),
                        value.path("name").asText(),
                        value.path("type").asText("TOOL"),
                        value.path("digest").asText(),
                        schema,
                        "QUARANTINED",
                        java.time.Instant.now());
                  })
              .toList();
      return new GatewayResult(root.path("policyStatus").asText("PASS"), values);
    } catch (AgentPlatformException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw AgentPlatformException.upstream();
    } catch (IOException | IllegalArgumentException exception) {
      throw AgentPlatformException.upstream();
    }
  }

  public record GatewayResult(String policyStatus, List<McpCapability> capabilities) {
    public GatewayResult {
      capabilities = List.copyOf(capabilities);
    }
  }
}
