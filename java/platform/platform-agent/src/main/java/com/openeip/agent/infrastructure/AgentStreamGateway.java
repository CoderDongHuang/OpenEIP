package com.openeip.agent.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.application.AgentExecutionService.ExecutionContext;
import com.openeip.agent.shared.exception.AgentException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Strict no-retry SSE bridge from the Java authorization boundary to Python Agent runtime. */
@Component
public class AgentStreamGateway {
  private static final int MAX_ANSWER_CHARS = 8000;
  private static final Set<String> EVENTS =
      Set.of(
          "execution.started",
          "tool.started",
          "tool.completed",
          "answer.delta",
          "execution.completed",
          "execution.error");

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String pythonUrl;
  private final String internalToken;
  private final String externalTenantId;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected ObjectMapper is application scoped.")
  public AgentStreamGateway(
      ObjectMapper objectMapper,
      @Value("${openeip.agent.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.agent.internal-token:}") String internalToken,
      @Value("${openeip.agent.external-tenant-id:11111111-1111-4111-8111-111111111111}")
          String externalTenantId) {
    this.objectMapper = objectMapper;
    this.pythonUrl = pythonUrl;
    this.internalToken = internalToken;
    this.externalTenantId = externalTenantId;
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
  }

  public StreamingResponseBody open(String userId, ExecutionContext context) {
    if (internalToken.isBlank()) {
      throw AgentException.upstream();
    }
    return output -> bridge(userId, context, output);
  }

  private void bridge(String userId, ExecutionContext context, OutputStream output)
      throws IOException {
    boolean terminal = false;
    try {
      HttpResponse<InputStream> response =
          httpClient.send(request(userId, context), HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        response.body().close();
        throw AgentException.upstream();
      }
      terminal = forward(context, response.body(), output);
      if (!terminal) {
        writeStableError(context, output, 0);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Agent stream interrupted", exception);
    } catch (AgentException | JsonProcessingException exception) {
      writeStableError(context, output, 0);
    } catch (IOException exception) {
      writeStableError(context, output, 0);
    }
  }

  private HttpRequest request(String userId, ExecutionContext context)
      throws JsonProcessingException {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("input", context.input());
    values.put("allowedTools", context.allowedTools());
    values.put("maxSteps", context.maxSteps());
    if (context.knowledgeBaseId() != null) {
      values.put("knowledgeBaseId", context.knowledgeBaseId());
    }
    String body = objectMapper.writeValueAsString(values);
    URI uri =
        URI.create(
            pythonUrl + "/api/v1/internal/agents/" + context.agentId() + "/executions:stream");
    return HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .header("X-OpenEIP-Internal-Token", internalToken)
        .header("X-Tenant-Id", externalTenantId)
        .header("X-User-Id", userId)
        .header("X-Execution-Id", context.executionId())
        .header("X-Request-Id", context.requestId())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
  }

  private boolean forward(ExecutionContext context, InputStream input, OutputStream output)
      throws IOException {
    try (input;
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      StreamState state = new StreamState();
      String event = null;
      String data = null;
      String line;
      try {
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty()) {
            if (event != null && data != null && process(context, state, event, data, output)) {
              return true;
            }
            event = null;
            data = null;
          } else if (line.startsWith("event: ") && event == null) {
            event = line.substring(7);
          } else if (line.startsWith("data: ") && data == null) {
            data = line.substring(6);
          } else {
            throw AgentException.upstream();
          }
        }
      } catch (AgentException | JsonProcessingException exception) {
        writeStableError(context, output, state.sequence);
        return true;
      }
      writeStableError(context, output, state.sequence);
      return true;
    }
  }

  private boolean process(
      ExecutionContext context,
      StreamState state,
      String event,
      String rawData,
      OutputStream output)
      throws IOException {
    if (!EVENTS.contains(event) || state.terminal) {
      throw AgentException.upstream();
    }
    JsonNode data = objectMapper.readTree(rawData);
    requireIdentityAndSequence(data, context, state.sequence);
    switch (event) {
      case "execution.started" -> validateStarted(data, context, state);
      case "tool.started" -> validateToolStarted(data, context, state);
      case "tool.completed" -> validateToolCompleted(data, context, state);
      case "answer.delta" -> validateAnswer(data, state);
      case "execution.completed" -> {
        validateCompleted(data, context, state);
        state.terminal = true;
      }
      case "execution.error" -> {
        validateError(data, state);
        writeStableError(context, output, state.sequence);
        state.terminal = true;
        state.sequence++;
        return true;
      }
      default -> throw AgentException.upstream();
    }
    writeEvent(event, data, output);
    state.sequence++;
    return state.terminal;
  }

  private static void validateStarted(JsonNode data, ExecutionContext context, StreamState state) {
    if (state.started
        || state.sequence != 0
        || data.size() != 4
        || !context.agentId().equals(text(data, "agentId"))) {
      throw AgentException.upstream();
    }
    state.started = true;
  }

  private static void validateToolStarted(
      JsonNode data, ExecutionContext context, StreamState state) {
    String callId = text(data, "toolCallId");
    String toolName = text(data, "toolName");
    int step = integer(data, "step");
    if (!state.started
        || data.size() != 6
        || !validUuid(callId)
        || !context.allowedTools().contains(toolName)
        || step != state.completedCalls + 1
        || step > context.maxSteps()
        || state.activeCall != null
        || !state.seenCallIds.add(callId)) {
      throw AgentException.upstream();
    }
    state.activeCall = new ActiveCall(callId, toolName, step);
  }

  private static void validateToolCompleted(
      JsonNode data, ExecutionContext context, StreamState state) {
    String callId = text(data, "toolCallId");
    String toolName = text(data, "toolName");
    int step = integer(data, "step");
    double duration = number(data, "durationMs");
    int resultChars = integer(data, "resultChars");
    ActiveCall activeCall = state.activeCall;
    if (!state.started
        || data.size() != 8
        || activeCall == null
        || !activeCall.callId().equals(callId)
        || !activeCall.toolName().equals(toolName)
        || activeCall.step() != step
        || duration < 0
        || duration > 15_000
        || resultChars < 1
        || resultChars > 8000) {
      throw AgentException.upstream();
    }
    state.activeCall = null;
    state.completedCalls++;
  }

  private static void validateAnswer(JsonNode data, StreamState state) {
    String value = text(data, "text");
    if (!state.started
        || data.size() != 4
        || state.activeCall != null
        || value.isEmpty()
        || value.length() > 1024
        || hasForbiddenControl(value)
        || state.answer.length() + value.length() > MAX_ANSWER_CHARS) {
      throw AgentException.upstream();
    }
    state.answer.append(value);
  }

  private static void validateCompleted(
      JsonNode data, ExecutionContext context, StreamState state) {
    int steps = integer(data, "steps");
    if (!state.started
        || data.size() != 5
        || state.activeCall != null
        || state.answer.isEmpty()
        || !"stop".equals(text(data, "finishReason"))
        || steps != state.completedCalls) {
      throw AgentException.upstream();
    }
  }

  private static void validateError(JsonNode data, StreamState state) {
    if (!state.started || data.size() != 5) {
      throw AgentException.upstream();
    }
    text(data, "code");
    text(data, "message");
  }

  private static void requireIdentityAndSequence(
      JsonNode data, ExecutionContext context, int sequence) {
    if (!context.requestId().equals(text(data, "requestId"))
        || !context.executionId().equals(text(data, "executionId"))
        || integer(data, "sequence") != sequence) {
      throw AgentException.upstream();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual()) {
      throw AgentException.upstream();
    }
    return value.textValue();
  }

  private static int integer(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw AgentException.upstream();
    }
    return value.intValue();
  }

  private static double number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
      throw AgentException.upstream();
    }
    return value.doubleValue();
  }

  private static boolean validUuid(String value) {
    try {
      return UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static boolean hasForbiddenControl(String value) {
    return value
        .chars()
        .anyMatch(
            character ->
                character < 32 && character != '\n' && character != '\r' && character != '\t');
  }

  private void writeStableError(ExecutionContext context, OutputStream output, int sequence)
      throws IOException {
    JsonNode data =
        objectMapper.valueToTree(
            Map.of(
                "requestId",
                context.requestId(),
                "executionId",
                context.executionId(),
                "sequence",
                sequence,
                "code",
                "AGENT-S-001",
                "message",
                "Agent execution failed"));
    writeEvent("execution.error", data, output);
  }

  private static void writeEvent(String event, JsonNode data, OutputStream output)
      throws IOException {
    String encoded = "event: " + event + "\ndata: " + data.toString() + "\n\n";
    output.write(encoded.getBytes(StandardCharsets.UTF_8));
    output.flush();
  }

  private static final class StreamState {
    private final Set<String> seenCallIds = new java.util.HashSet<>();
    private final StringBuilder answer = new StringBuilder();
    private int sequence;
    private int completedCalls;
    private boolean started;
    private boolean terminal;
    private ActiveCall activeCall;
  }

  private record ActiveCall(String callId, String toolName, int step) {}
}
