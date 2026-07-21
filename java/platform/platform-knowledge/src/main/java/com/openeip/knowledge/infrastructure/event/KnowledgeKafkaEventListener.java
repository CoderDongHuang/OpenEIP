package com.openeip.knowledge.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.knowledge.application.KnowledgeBaseService;
import com.openeip.knowledge.application.KnowledgeEventService;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Strict JSON adapter for versioned parsed and embedding-completed topics. */
@Component
@ConditionalOnProperty(prefix = "openeip.knowledge.kafka", name = "enabled", havingValue = "true")
public class KnowledgeKafkaEventListener {
  private final KnowledgeEventService events;
  private final ObjectMapper mapper;
  private final String externalTenantId;

  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
      justification = "Spring service is shared; the ObjectMapper is defensively copied.")
  public KnowledgeKafkaEventListener(
      KnowledgeEventService events,
      ObjectMapper mapper,
      @Value("${openeip.knowledge.kafka.external-tenant-id}") String externalTenantId) {
    KnowledgeBaseService.validExternalTenantUuid(externalTenantId);
    this.events = events;
    this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.externalTenantId = externalTenantId;
  }

  @KafkaListener(
      topics = "${openeip.knowledge.kafka.parsed-topic}",
      groupId = "${openeip.knowledge.kafka.group-id}")
  public void parsed(String json) {
    ParsedEvent event = read(json, ParsedEvent.class);
    require(
        "document.lifecycle.parsed".equals(event.eventType())
            && event.eventVersion() == 1
            && "python-engine-document".equals(event.source()),
        "Invalid parsed event contract");
    requireTenant(event.tenantId());
    events.processParsed(
        event.eventId(),
        KnowledgeBaseService.MVP_TENANT,
        event.payload().documentId(),
        fingerprint(event.payload()));
  }

  @KafkaListener(
      topics = "${openeip.knowledge.kafka.embedding-topic}",
      groupId = "${openeip.knowledge.kafka.group-id}")
  public void embeddingCompleted(String json) {
    EmbeddingEvent event = read(json, EmbeddingEvent.class);
    require(
        "embedding.job.completed".equals(event.eventType())
            && event.eventVersion() == 1
            && "python-engine-embedding".equals(event.source()),
        "Invalid embedding event contract");
    requireTenant(event.tenantId());
    events.processEmbeddingCompleted(
        event.eventId(),
        KnowledgeBaseService.MVP_TENANT,
        event.payload().knowledgeBaseId(),
        event.payload().documentId(),
        fingerprint(event.payload()));
  }

  private <T> T read(String json, Class<T> type) {
    if (json == null || json.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
      throw KnowledgeException.invalid("Invalid event envelope");
    }
    try {
      return mapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw KnowledgeException.invalid("Invalid event envelope");
    }
  }

  private String fingerprint(Object payload) {
    try {
      byte[] canonical = mapper.writeValueAsBytes(payload);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Unable to fingerprint event payload", exception);
    }
  }

  private void requireTenant(String tenantId) {
    require(externalTenantId.equals(tenantId), "Unknown event tenant");
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw KnowledgeException.invalid(message);
    }
  }

  public record ParsedEvent(
      String eventId,
      String eventType,
      int eventVersion,
      Instant timestamp,
      String source,
      String tenantId,
      String userId,
      String traceId,
      String idempotencyKey,
      ParsedPayload payload) {}

  public record ParsedPayload(
      String documentId,
      String sourceType,
      String sourceSha256,
      String normalizedTextSha256,
      int chunkCount,
      String parserVersion) {}

  public record EmbeddingEvent(
      String eventId,
      String eventType,
      int eventVersion,
      Instant timestamp,
      String source,
      String tenantId,
      String traceId,
      EmbeddingPayload payload) {}

  public record EmbeddingPayload(
      String jobId,
      String knowledgeBaseId,
      String documentId,
      int chunkCount,
      String embeddingModel,
      String modelVersion,
      int vectorDimension) {}
}
