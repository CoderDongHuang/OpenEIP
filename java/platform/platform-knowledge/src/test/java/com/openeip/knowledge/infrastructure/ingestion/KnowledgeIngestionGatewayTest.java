package com.openeip.knowledge.infrastructure.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.ParsedChunk;
import com.openeip.knowledge.shared.exception.KnowledgeIngestionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionGatewayTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT = "22222222-2222-4222-8222-222222222222";
  private static final String BASE = "33333333-3333-4333-8333-333333333333";
  private static final String DOCUMENT = "44444444-4444-4444-8444-444444444444";
  private static final String TOKEN = "internal-test-token";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private ExecutorService executor;
  private final List<CapturedRequest> requests = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newSingleThreadExecutor();
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
    executor.shutdownNow();
  }

  @Test
  void parsesTextAndEmbedsInBoundedAuthenticatedBatches() {
    KnowledgeIngestionGateway gateway = gateway(TOKEN);

    var parsed =
        gateway.parse(USER, DOCUMENT, "text/plain", "source".getBytes(StandardCharsets.UTF_8));
    List<ParsedChunk> chunks = new ArrayList<>();
    for (int index = 0; index < 33; index++) {
      chunks.add(
          new ParsedChunk(
              "chk_" + "%032x".formatted(index), "chunk " + index, "%064x".formatted(index)));
    }
    int vectorCount = gateway.embed(USER, BASE, DOCUMENT, chunks);

    assertThat(parsed.sourceType()).isEqualTo("text");
    assertThat(parsed.fingerprint()).isEqualTo("a".repeat(64));
    assertThat(parsed.chunks()).hasSize(1);
    assertThat(vectorCount).isEqualTo(33);
    assertThat(requests).hasSize(3);
    assertThat(requests.get(0).path()).isEqualTo("/api/v1/parsing/documents");
    assertThat(requests.get(1).json().path("chunks")).hasSize(32);
    assertThat(requests.get(2).json().path("chunks")).hasSize(1);
    assertThat(requests)
        .allSatisfy(
            request -> {
              assertThat(request.internalToken()).isEqualTo(TOKEN);
              assertThat(request.tenantId()).isEqualTo(TENANT);
              assertThat(request.userId()).isEqualTo(USER);
            });
  }

  @Test
  void sendsImagesThroughOcrBeforeParsing() {
    KnowledgeIngestionGateway gateway = gateway(TOKEN);

    var parsed = gateway.parse(USER, DOCUMENT, "image/png", new byte[] {1, 2, 3});

    assertThat(parsed.sourceType()).isEqualTo("ocr");
    assertThat(requests)
        .extracting(CapturedRequest::path)
        .containsExactly("/api/v1/ocr/recognitions", "/api/v1/parsing/documents");
    assertThat(requests.get(0).contentType()).isEqualTo("image/png");
    assertThat(requests.get(1).contentType())
        .isEqualTo("application/vnd.openeip.ocr-result.v1+json");
    assertThat(requests.get(1).documentId()).isEqualTo(DOCUMENT);
  }

  @Test
  void rejectsUnsupportedInputMissingCredentialsAndInvalidUpstreamContracts() {
    KnowledgeIngestionGateway gateway = gateway(TOKEN);

    assertThatThrownBy(() -> gateway.parse(USER, DOCUMENT, "application/pdf", new byte[] {1}))
        .isInstanceOf(KnowledgeIngestionException.class)
        .hasMessage("Document type is not processable");
    assertThatThrownBy(() -> gateway("").parse(USER, DOCUMENT, "text/plain", new byte[] {1}))
        .isInstanceOf(KnowledgeIngestionException.class)
        .hasMessage("Document processing is unavailable");
    assertThatThrownBy(() -> gateway.parse(USER, "invalid-contract", "text/plain", new byte[] {1}))
        .isInstanceOf(KnowledgeIngestionException.class)
        .hasMessage("Document processing is unavailable");
  }

  private KnowledgeIngestionGateway gateway(String token) {
    return new KnowledgeIngestionGateway(
        MAPPER, "http://127.0.0.1:" + server.getAddress().getPort() + "/", token, TENANT);
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] requestBody = exchange.getRequestBody().readAllBytes();
    String path = exchange.getRequestURI().getPath();
    requests.add(
        new CapturedRequest(
            path,
            exchange.getRequestHeaders().getFirst("Content-Type"),
            exchange.getRequestHeaders().getFirst("X-OpenEIP-Internal-Token"),
            exchange.getRequestHeaders().getFirst("X-Tenant-Id"),
            exchange.getRequestHeaders().getFirst("X-User-Id"),
            exchange.getRequestHeaders().getFirst("X-Document-Id"),
            requestBody));

    JsonNode data;
    if ("/api/v1/ocr/recognitions".equals(path)) {
      data = MAPPER.createObjectNode().put("text", "recognized source");
    } else if ("/api/v1/parsing/documents".equals(path)) {
      String requestedDocument = exchange.getRequestHeaders().getFirst("X-Document-Id");
      String responseDocument =
          "invalid-contract".equals(requestedDocument) ? DOCUMENT : requestedDocument;
      data =
          MAPPER
              .createObjectNode()
              .put("documentId", responseDocument)
              .put(
                  "sourceType",
                  exchange.getRequestHeaders().getFirst("Content-Type").startsWith("text/plain")
                      ? "TEXT"
                      : "OCR")
              .put("normalizedTextSha256", "a".repeat(64))
              .set(
                  "chunks",
                  MAPPER
                      .createArrayNode()
                      .add(
                          MAPPER
                              .createObjectNode()
                              .put("chunkId", "chk_" + "b".repeat(32))
                              .put("text", "source")
                              .put("sha256", "c".repeat(64))));
    } else {
      JsonNode request = MAPPER.readTree(requestBody);
      data =
          MAPPER
              .createObjectNode()
              .put("knowledgeBaseId", request.path("knowledgeBaseId").asText())
              .put("documentId", request.path("documentId").asText())
              .put("vectorCount", request.path("chunks").size());
    }
    byte[] response =
        MAPPER.writeValueAsBytes(
            MAPPER.createObjectNode().put("code", 0).put("message", "success").set("data", data));
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private record CapturedRequest(
      String path,
      String contentType,
      String internalToken,
      String tenantId,
      String userId,
      String documentId,
      byte[] body) {
    private CapturedRequest {
      body = body.clone();
    }

    private JsonNode json() {
      try {
        return MAPPER.readTree(body);
      } catch (IOException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
