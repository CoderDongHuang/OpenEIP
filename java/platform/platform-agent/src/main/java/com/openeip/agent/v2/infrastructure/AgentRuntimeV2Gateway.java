package com.openeip.agent.v2.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.shared.AgentPlatformException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "The application-scoped Jackson mapper is intentionally injected and shared.")
public class AgentRuntimeV2Gateway {
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final String pythonUrl;
  private final String internalToken;

  public AgentRuntimeV2Gateway(
      ObjectMapper mapper,
      @Value("${openeip.agent.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.agent.internal-token:}") String internalToken) {
    this.mapper = mapper;
    this.pythonUrl = pythonUrl;
    this.internalToken = internalToken;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public RuntimeResult execute(
      String capability, String input, String agentType, int agentVersion) {
    if (internalToken.isBlank()) {
      throw AgentPlatformException.upstream();
    }
    try {
      String body =
          mapper.writeValueAsString(
              Map.of("input", input, "agentType", agentType, "agentVersion", agentVersion));
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(pythonUrl + "/api/v2/internal/agent-runs:execute"))
              .timeout(Duration.ofSeconds(35))
              .header("Content-Type", "application/json")
              .header("X-OpenEIP-Internal-Token", internalToken)
              .header("X-Agent-Capability", capability)
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200 || response.body().length() > 1_048_576) {
        throw AgentPlatformException.upstream();
      }
      JsonNode value = mapper.readTree(response.body());
      if (!value.isObject()
          || !value.hasNonNull("status")
          || !value.path("events").isArray()
          || value.path("events").size() > 256) {
        throw AgentPlatformException.upstream();
      }
      List<RuntimeEvent> events =
          mapper.readerForListOf(RuntimeEvent.class).readValue(value.path("events"));
      return new RuntimeResult(
          value.path("status").asText(),
          value.path("failureCode").isNull() ? null : value.path("failureCode").asText(),
          events);
    } catch (AgentPlatformException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw AgentPlatformException.upstream();
    } catch (Exception exception) {
      throw AgentPlatformException.upstream();
    }
  }

  public record RuntimeResult(String status, String failureCode, List<RuntimeEvent> events) {
    public RuntimeResult {
      events = List.copyOf(events);
    }
  }

  public record RuntimeEvent(String type, JsonNode payload) {}
}
