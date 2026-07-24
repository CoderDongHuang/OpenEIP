package com.openeip.knowledge.infrastructure.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.knowledge.application.KnowledgeBaseService;
import com.openeip.knowledge.shared.exception.KnowledgeIngestionException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Strict bounded REST adapter for the internal OCR, parsing, and embedding contracts. */
@Component
public class KnowledgeIngestionGateway {
  private static final String OCR_RESULT_MEDIA_TYPE = "application/vnd.openeip.ocr-result.v1+json";
  private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;
  private static final int EMBEDDING_BATCH_SIZE = 32;
  private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
  private static final Pattern CHUNK_ID = Pattern.compile("^chk_[a-f0-9]{32}$");

  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final URI pythonBaseUri;
  private final String internalToken;
  private final String externalTenantId;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected ObjectMapper is application scoped.")
  public KnowledgeIngestionGateway(
      ObjectMapper mapper,
      @Value("${openeip.knowledge.ingestion.python-url:http://python:8000}") String pythonUrl,
      @Value("${openeip.knowledge.ingestion.internal-token:}") String internalToken,
      @Value(
              "${openeip.knowledge.ingestion.external-tenant-id:11111111-1111-4111-8111-111111111111}")
          String externalTenantId) {
    this.mapper = mapper;
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    this.pythonBaseUri = URI.create(withoutTrailingSlash(pythonUrl));
    this.internalToken = internalToken;
    KnowledgeBaseService.validExternalTenantUuid(externalTenantId);
    this.externalTenantId = externalTenantId;
  }

  public ParsedDocument parse(
      String userId, String documentId, String contentType, byte[] content) {
    byte[] parsingInput = content.clone();
    String parsingContentType = contentType;
    if ("image/png".equals(contentType) || "image/jpeg".equals(contentType)) {
      JsonNode ocr = post("/api/v1/ocr/recognitions", contentType, content, userId, Map.of());
      try {
        parsingInput = mapper.writeValueAsBytes(ocr);
      } catch (JsonProcessingException exception) {
        throw KnowledgeIngestionException.upstream();
      }
      parsingContentType = OCR_RESULT_MEDIA_TYPE;
    } else if (!isDirectlyParsable(contentType)) {
      throw KnowledgeIngestionException.unsupported();
    }

    JsonNode parsed =
        post(
            "/api/v1/parsing/documents",
            parsingContentType,
            parsingInput,
            userId,
            Map.of("X-Document-Id", documentId));
    return parsed(documentId, parsed);
  }

  public int embed(
      String userId, String knowledgeBaseId, String documentId, List<ParsedChunk> chunks) {
    int total = 0;
    for (int offset = 0; offset < chunks.size(); offset += EMBEDDING_BATCH_SIZE) {
      List<ParsedChunk> batch =
          chunks.subList(offset, Math.min(chunks.size(), offset + EMBEDDING_BATCH_SIZE));
      List<Map<String, Object>> encodedChunks =
          batch.stream()
              .map(
                  chunk ->
                      Map.<String, Object>of(
                          "chunkId", chunk.chunkId(),
                          "text", chunk.text(),
                          "sourceSha256", chunk.sha256(),
                          "pages", chunk.pages(),
                          "startChar", chunk.startChar(),
                          "endChar", chunk.endChar()))
              .toList();
      byte[] body;
      try {
        body =
            mapper.writeValueAsBytes(
                Map.of(
                    "jobId", UUID.randomUUID().toString(),
                    "knowledgeBaseId", knowledgeBaseId,
                    "documentId", documentId,
                    "chunks", encodedChunks));
      } catch (JsonProcessingException exception) {
        throw KnowledgeIngestionException.upstream();
      }
      JsonNode result =
          post("/api/v1/embedding/batches", "application/json", body, userId, Map.of());
      requireText(result, "knowledgeBaseId", knowledgeBaseId);
      requireText(result, "documentId", documentId);
      if (result.path("vectorCount").asInt(-1) != batch.size()) {
        throw KnowledgeIngestionException.upstream();
      }
      total += batch.size();
    }
    return total;
  }

  public SearchResult search(
      String userId, String knowledgeBaseId, String query, String mode, int topK) {
    byte[] body;
    try {
      body =
          mapper.writeValueAsBytes(
              Map.of(
                  "knowledgeBaseId", knowledgeBaseId, "query", query, "mode", mode, "topK", topK));
    } catch (JsonProcessingException exception) {
      throw KnowledgeIngestionException.upstream();
    }
    JsonNode data = post("/api/v1/retrieval/search", "application/json", body, userId, Map.of());
    if (!mode.equals(requiredText(data, "mode"))
        || !data.path("results").isArray()
        || data.path("results").size() > 50) {
      throw KnowledgeIngestionException.upstream();
    }
    List<SearchHit> hits = new ArrayList<>();
    for (JsonNode value : data.path("results")) {
      String documentId = requiredText(value, "documentId");
      String chunkId = requiredText(value, "chunkId");
      String sourceSha256 = requiredText(value, "sourceSha256");
      String excerpt = requiredText(value, "excerpt");
      JsonNode pagesNode = value.path("pages");
      double score = value.path("score").asDouble(Double.NaN);
      int startChar = value.path("startChar").asInt(-1);
      int endChar = value.path("endChar").asInt(-1);
      if (!isUuid(documentId)
          || !CHUNK_ID.matcher(chunkId).matches()
          || !SHA256.matcher(sourceSha256).matches()
          || excerpt.length() > 500
          || !pagesNode.isArray()
          || pagesNode.size() > 100
          || !Double.isFinite(score)
          || startChar < 0
          || endChar <= startChar) {
        throw KnowledgeIngestionException.upstream();
      }
      List<Integer> pages = new ArrayList<>();
      pagesNode.forEach(page -> pages.add(page.asInt()));
      if (pages.stream().anyMatch(page -> page < 1)) {
        throw KnowledgeIngestionException.upstream();
      }
      hits.add(
          new SearchHit(
              documentId, chunkId, sourceSha256, score, excerpt, pages, startChar, endChar));
    }
    return new SearchResult(mode, List.copyOf(hits));
  }

  private JsonNode post(
      String path,
      String contentType,
      byte[] body,
      String userId,
      Map<String, String> additionalHeaders) {
    if (internalToken.isBlank()) {
      throw KnowledgeIngestionException.upstream();
    }
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(pythonBaseUri.resolve(path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", contentType)
            .header("Accept", "application/json")
            .header("X-OpenEIP-Internal-Token", internalToken)
            .header("X-Tenant-Id", externalTenantId)
            .header("X-User-Id", userId)
            .header("X-Request-Id", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    additionalHeaders.forEach(builder::header);
    try {
      HttpResponse<InputStream> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream input = response.body()) {
        byte[] responseBody = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (response.statusCode() != 200 || responseBody.length > MAX_RESPONSE_BYTES) {
          throw KnowledgeIngestionException.upstream();
        }
        JsonNode envelope = mapper.readTree(responseBody);
        if (envelope == null
            || !envelope.isObject()
            || envelope.path("code").asInt(-1) != 0
            || !"success".equals(envelope.path("message").asText())
            || !envelope.path("data").isObject()) {
          throw KnowledgeIngestionException.upstream();
        }
        return envelope.path("data");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw KnowledgeIngestionException.upstream();
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof KnowledgeIngestionException ingestionException) {
        throw ingestionException;
      }
      throw KnowledgeIngestionException.upstream();
    }
  }

  private static ParsedDocument parsed(String documentId, JsonNode data) {
    requireText(data, "documentId", documentId);
    String sourceType = requiredText(data, "sourceType");
    String fingerprint = requiredText(data, "normalizedTextSha256");
    if ((!Set.of("TEXT", "OCR", "PDF", "DOCX", "PPTX", "XLSX").contains(sourceType))
        || !SHA256.matcher(fingerprint).matches()
        || !data.path("chunks").isArray()
        || data.path("chunks").isEmpty()
        || data.path("chunks").size() > 10_000) {
      throw KnowledgeIngestionException.upstream();
    }
    List<ParsedChunk> chunks = new ArrayList<>();
    for (JsonNode value : data.path("chunks")) {
      String chunkId = requiredText(value, "chunkId");
      String text = requiredText(value, "text");
      String sha256 = requiredText(value, "sha256");
      if (!value.isObject()
          || !CHUNK_ID.matcher(chunkId).matches()
          || text.length() > 8192
          || !SHA256.matcher(sha256).matches()) {
        throw KnowledgeIngestionException.upstream();
      }
      JsonNode pagesNode = value.path("pages");
      if (!pagesNode.isArray() || pagesNode.size() > 100) {
        throw KnowledgeIngestionException.upstream();
      }
      List<Integer> pages = new ArrayList<>();
      pagesNode.forEach(page -> pages.add(page.asInt()));
      int startChar = value.path("startChar").asInt(-1);
      int endChar = value.path("endChar").asInt(-1);
      if (pages.stream().anyMatch(page -> page < 1) || startChar < 0 || endChar <= startChar) {
        throw KnowledgeIngestionException.upstream();
      }
      chunks.add(new ParsedChunk(chunkId, text, sha256, List.copyOf(pages), startChar, endChar));
    }
    return new ParsedDocument(
        sourceType.toLowerCase(Locale.ROOT), fingerprint, List.copyOf(chunks));
  }

  private static void requireText(JsonNode data, String field, String expected) {
    if (!expected.equals(requiredText(data, field))) {
      throw KnowledgeIngestionException.upstream();
    }
  }

  private static String requiredText(JsonNode data, String field) {
    JsonNode value = data.get(field);
    if (value == null || !value.isTextual() || value.textValue().isEmpty()) {
      throw KnowledgeIngestionException.upstream();
    }
    return value.textValue();
  }

  private static String withoutTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private static boolean isDirectlyParsable(String contentType) {
    return Set.of(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .contains(contentType);
  }

  public record ParsedDocument(String sourceType, String fingerprint, List<ParsedChunk> chunks) {
    public ParsedDocument {
      chunks = List.copyOf(chunks);
    }

    @Override
    public List<ParsedChunk> chunks() {
      return List.copyOf(chunks);
    }
  }

  public record ParsedChunk(
      String chunkId, String text, String sha256, List<Integer> pages, int startChar, int endChar) {
    public ParsedChunk(String chunkId, String text, String sha256) {
      this(chunkId, text, sha256, List.of(1), 0, text.length());
    }

    public ParsedChunk {
      pages = List.copyOf(pages);
    }
  }

  public record SearchHit(
      String documentId,
      String chunkId,
      String sourceSha256,
      double score,
      String excerpt,
      List<Integer> pages,
      int startChar,
      int endChar) {
    public SearchHit {
      pages = List.copyOf(pages);
    }
  }

  public record SearchResult(String mode, List<SearchHit> results) {
    public SearchResult {
      results = List.copyOf(results);
    }
  }
}
