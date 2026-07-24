package com.openeip.chat.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.chat.application.ChatSessionService;
import com.openeip.chat.application.ChatSessionService.StreamContext;
import com.openeip.chat.shared.exception.ChatException;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Validating no-retry SSE bridge from the Java user boundary to the Python internal API. */
@Component
public class ChatStreamGateway {
  private static final int MAX_ANSWER_CHARS = 8000;
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
  private static final Pattern CHUNK_ID_PATTERN = Pattern.compile("^chk_[a-f0-9]{32}$");
  private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

  private final ChatSessionService sessions;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final URI streamUri;
  private final String internalToken;
  private final String externalTenantId;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are immutable or application-scoped.")
  public ChatStreamGateway(
      ChatSessionService sessions,
      ObjectMapper objectMapper,
      @Value("${openeip.chat.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.chat.internal-token:}") String internalToken,
      @Value("${openeip.chat.external-tenant-id:11111111-1111-4111-8111-111111111111}")
          String externalTenantId) {
    this.sessions = sessions;
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    this.streamUri = URI.create(pythonUrl + "/api/v1/internal/chat/messages:stream");
    this.internalToken = internalToken;
    this.externalTenantId = externalTenantId;
  }

  public StreamingResponseBody open(String userId, StreamContext context) {
    if (internalToken.isBlank()) {
      sessions.cancel(userId, context.sessionId(), context.requestId());
      throw ChatException.upstream();
    }
    return output -> bridge(userId, context, output);
  }

  private void bridge(String userId, StreamContext context, OutputStream output)
      throws IOException {
    boolean terminal = false;
    try {
      HttpRequest request = request(userId, context);
      HttpResponse<InputStream> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
        response.body().close();
        throw ChatException.upstream();
      }
      terminal = forward(userId, context, response.body(), output);
      if (!terminal) {
        writeStableError(context, output);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Chat stream interrupted", exception);
    } catch (ChatException | JsonProcessingException exception) {
      writeStableError(context, output);
    } catch (IOException exception) {
      writeStableError(context, output);
    } finally {
      if (!terminal) {
        cancelQuietly(userId, context);
      }
    }
  }

  private HttpRequest request(String userId, StreamContext context) throws JsonProcessingException {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "knowledgeBaseId", context.knowledgeBaseId(),
                "message", context.message(),
                "topK", context.topK()));
    return HttpRequest.newBuilder(streamUri)
        .timeout(Duration.ofMinutes(10))
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .header("X-OpenEIP-Internal-Token", internalToken)
        .header("X-Tenant-Id", externalTenantId)
        .header("X-User-Id", userId)
        .header("X-Session-Id", context.sessionId())
        .header("X-Request-Id", context.requestId())
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build();
  }

  private boolean forward(
      String userId, StreamContext context, InputStream input, OutputStream output)
      throws IOException {
    try (input;
        var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String event = null;
      String data = null;
      int expectedSequence = 0;
      StringBuilder answer = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          if (event != null && data != null) {
            ProcessedEvent processed =
                process(userId, context, event, data, expectedSequence, answer, output);
            expectedSequence = processed.nextSequence();
            if (processed.terminal()) {
              return true;
            }
          }
          event = null;
          data = null;
        } else if (line.startsWith("event: ") && event == null) {
          event = line.substring(7);
        } else if (line.startsWith("data: ") && data == null) {
          data = line.substring(6);
        } else {
          throw ChatException.upstream();
        }
      }
      return false;
    }
  }

  private ProcessedEvent process(
      String userId,
      StreamContext context,
      String event,
      String rawData,
      int expectedSequence,
      StringBuilder answer,
      OutputStream output)
      throws IOException {
    JsonNode data = objectMapper.readTree(rawData);
    requireIdentity(data, context);
    return switch (event) {
      case "token" -> {
        String token = text(data, "token");
        if (data.path("sequence").asInt(-1) != expectedSequence
            || token.isEmpty()
            || token.length() > 1024
            || answer.length() + token.length() > MAX_ANSWER_CHARS) {
          throw ChatException.upstream();
        }
        answer.append(token);
        writeEvent("token", data, output);
        yield new ProcessedEvent(expectedSequence + 1, false);
      }
      case "done" -> {
        if (!"stop".equals(text(data, "finishReason"))
            || !data.path("citations").isArray()
            || data.path("citations").size() > 20
            || answer.isEmpty()
            || !validCitations(data.path("citations"))) {
          throw ChatException.upstream();
        }
        sessions.complete(userId, context.sessionId(), context.requestId(), answer.toString());
        writeEvent("done", data, output);
        yield new ProcessedEvent(expectedSequence, true);
      }
      case "error" -> {
        sessions.cancel(userId, context.sessionId(), context.requestId());
        writeStableError(context, output);
        yield new ProcessedEvent(expectedSequence, true);
      }
      default -> throw ChatException.upstream();
    };
  }

  private static boolean validCitations(JsonNode citations) {
    Set<String> chunkIds = new HashSet<>();
    for (JsonNode citation : citations) {
      if (!citation.isObject() || citation.size() != 8) {
        return false;
      }
      String documentId = optionalText(citation, "documentId");
      String chunkId = optionalText(citation, "chunkId");
      String sourceSha256 = optionalText(citation, "sourceSha256");
      String excerpt = optionalText(citation, "excerpt");
      JsonNode scoreNode = citation.get("score");
      JsonNode pagesNode = citation.get("pages");
      JsonNode startCharNode = citation.get("startChar");
      JsonNode endCharNode = citation.get("endChar");
      if (!UUID_PATTERN.matcher(documentId).matches()
          || !CHUNK_ID_PATTERN.matcher(chunkId).matches()
          || !SHA256_PATTERN.matcher(sourceSha256).matches()
          || scoreNode == null
          || !scoreNode.isNumber()
          || excerpt.length() > 500
          || pagesNode == null
          || !pagesNode.isArray()
          || pagesNode.size() > 100
          || startCharNode == null
          || !startCharNode.isIntegralNumber()
          || !startCharNode.canConvertToInt()
          || endCharNode == null
          || !endCharNode.isIntegralNumber()
          || !endCharNode.canConvertToInt()) {
        return false;
      }
      double score = scoreNode.doubleValue();
      int startChar = startCharNode.intValue();
      int endChar = endCharNode.intValue();
      if (!Double.isFinite(score)
          || score < -1
          || score > 1
          || startChar < 0
          || endChar <= startChar
          || !validPages(pagesNode)
          || !chunkIds.add(chunkId)) {
        return false;
      }
    }
    return true;
  }

  private static boolean validPages(JsonNode pages) {
    for (JsonNode page : pages) {
      if (!page.isIntegralNumber() || !page.canConvertToInt() || page.intValue() < 1) {
        return false;
      }
    }
    return true;
  }

  private static String optionalText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.textValue() : "";
  }

  private static void requireIdentity(JsonNode data, StreamContext context) {
    if (!context.requestId().equals(text(data, "requestId"))
        || !context.sessionId().equals(text(data, "sessionId"))) {
      throw ChatException.upstream();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual()) {
      throw ChatException.upstream();
    }
    return value.textValue();
  }

  private void writeStableError(StreamContext context, OutputStream output) throws IOException {
    JsonNode data =
        objectMapper.valueToTree(
            Map.of(
                "requestId",
                context.requestId(),
                "sessionId",
                context.sessionId(),
                "code",
                "CHAT-S-001",
                "message",
                "Chat generation failed"));
    writeEvent("error", data, output);
  }

  private static void writeEvent(String event, JsonNode data, OutputStream output)
      throws IOException {
    String encoded = "event: " + event + "\ndata: " + data.toString() + "\n\n";
    output.write(encoded.getBytes(StandardCharsets.UTF_8));
    output.flush();
  }

  private void cancelQuietly(String userId, StreamContext context) {
    try {
      sessions.cancel(userId, context.sessionId(), context.requestId());
    } catch (RuntimeException ignored) {
      // The client is already disconnected; do not replace the transport failure.
    }
  }

  private record ProcessedEvent(int nextSequence, boolean terminal) {}
}
