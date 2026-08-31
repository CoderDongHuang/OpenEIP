package com.openeip.agent.v2.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.domain.AgentPlatformModels.EvaluationCase;
import com.openeip.agent.v2.domain.AgentPlatformModels.Gate;
import com.openeip.agent.v2.domain.AgentPlatformModels.Metric;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "The application-scoped Jackson mapper is intentionally injected and shared.")
public class AgentEvaluationGateway {
  private final ObjectMapper mapper;
  private final HttpClient client;
  private final String pythonUrl;
  private final String internalToken;

  public AgentEvaluationGateway(
      ObjectMapper mapper,
      @Value("${openeip.agent.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.agent.internal-token:}") String internalToken) {
    this.mapper = mapper;
    this.pythonUrl = pythonUrl;
    this.internalToken = internalToken;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  public EvaluationResult run(
      String suiteDigest,
      String candidateDigest,
      String baselineDigest,
      int repeatCount,
      List<EvaluationCase> cases,
      JsonNode gatePolicy) {
    if (internalToken.isBlank()) {
      throw AgentPlatformException.upstream();
    }
    try {
      List<Map<String, Object>> wireCases =
          cases.stream()
              .map(
                  value ->
                      Map.<String, Object>of(
                          "key", value.key(),
                          "agentType", value.agentType(),
                          "fixture", read(value.fixtureJson()),
                          "assertions", read(value.assertionsJson()),
                          "digest", value.digest()))
              .toList();
      String body =
          mapper.writeValueAsString(
              Map.of(
                  "suiteDigest", suiteDigest,
                  "candidateDigest", candidateDigest,
                  "baselineDigest", baselineDigest,
                  "repeatCount", repeatCount,
                  "cases", wireCases,
                  "gatePolicy", gatePolicy));
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(pythonUrl + "/api/v2/internal/evaluations:run"))
              .timeout(Duration.ofSeconds(35))
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
      List<Metric> metrics =
          java.util.stream.StreamSupport.stream(root.path("metrics").spliterator(), false)
              .map(
                  node ->
                      new Metric(
                          node.path("key").asText(),
                          node.path("value").asDouble(),
                          node.path("sampleCount").asInt(),
                          node.hasNonNull("low") ? node.path("low").asDouble() : null,
                          node.hasNonNull("high") ? node.path("high").asDouble() : null))
              .toList();
      List<Gate> gates =
          java.util.stream.StreamSupport.stream(root.path("gates").spliterator(), false)
              .map(
                  node ->
                      new Gate(
                          node.path("key").asText(),
                          node.path("status").asText(),
                          node.hasNonNull("actual") ? node.path("actual").asDouble() : null,
                          node.hasNonNull("threshold") ? node.path("threshold").asDouble() : null,
                          node.path("reasonCode").asText()))
              .toList();
      if (metrics.isEmpty() || gates.isEmpty()) {
        throw AgentPlatformException.upstream();
      }
      String status =
          gates.stream().allMatch(gate -> "PASS".equals(gate.status())) ? "PASS" : "FAIL";
      return new EvaluationResult(status, metrics, gates);
    } catch (AgentPlatformException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw AgentPlatformException.upstream();
    } catch (IOException | IllegalArgumentException exception) {
      throw AgentPlatformException.upstream();
    }
  }

  public record EvaluationResult(String gateStatus, List<Metric> metrics, List<Gate> gates) {
    public EvaluationResult {
      metrics = List.copyOf(metrics);
      gates = List.copyOf(gates);
    }
  }

  private JsonNode read(String value) {
    try {
      return mapper.readTree(value);
    } catch (IOException exception) {
      throw AgentPlatformException.upstream();
    }
  }
}
