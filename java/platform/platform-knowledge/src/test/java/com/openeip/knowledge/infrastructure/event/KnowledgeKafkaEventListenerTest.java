package com.openeip.knowledge.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openeip.knowledge.application.KnowledgeEventService;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeKafkaEventListenerTest {
  private static final String TENANT = "11111111-1111-4111-8111-111111111111";
  private static final String EVENT = "22222222-2222-4222-8222-222222222222";
  private static final String DOCUMENT = "33333333-3333-4333-8333-333333333333";
  private static final String BASE = "44444444-4444-4444-8444-444444444444";

  @Mock KnowledgeEventService events;
  private KnowledgeKafkaEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new KnowledgeKafkaEventListener(
            events, new ObjectMapper().registerModule(new JavaTimeModule()), TENANT);
  }

  @Test
  void mapsStrictParsedAndEmbeddingContractsToInternalTenant() {
    listener.parsed(parsed("document.lifecycle.parsed", TENANT));
    verify(events)
        .processParsed(
            org.mockito.ArgumentMatchers.eq(EVENT),
            org.mockito.ArgumentMatchers.eq("default"),
            org.mockito.ArgumentMatchers.eq(DOCUMENT),
            anyString());

    listener.embeddingCompleted(embedding("embedding.job.completed", TENANT));
    verify(events)
        .processEmbeddingCompleted(
            org.mockito.ArgumentMatchers.eq(EVENT),
            org.mockito.ArgumentMatchers.eq("default"),
            org.mockito.ArgumentMatchers.eq(BASE),
            org.mockito.ArgumentMatchers.eq(DOCUMENT),
            anyString());
  }

  @Test
  void rejectsUnknownFieldsForgedTypeTenantAndOversizedEnvelope() {
    assertThatThrownBy(() -> listener.parsed(parsed("forged", TENANT)))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(
            () ->
                listener.parsed(
                    parsed("document.lifecycle.parsed", "55555555-5555-4555-8555-555555555555")))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(
            () ->
                listener.parsed(
                    parsed("document.lifecycle.parsed", TENANT).replace("}", ",\"extra\":1}")))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> listener.parsed("x".repeat(65 * 1024)))
        .isInstanceOf(KnowledgeException.class);
  }

  @Test
  void rejectsInvalidConfiguredTenantAtStartup() {
    assertThatThrownBy(
            () ->
                new KnowledgeKafkaEventListener(
                    events, new ObjectMapper().registerModule(new JavaTimeModule()), "default"))
        .isInstanceOf(KnowledgeException.class);
  }

  private static String parsed(String type, String tenant) {
    return """
        {"eventId":"__EVENT__","eventType":"__TYPE__","eventVersion":1,
         "timestamp":"2026-07-22T00:00:00Z","source":"python-engine-document",
         "tenantId":"__TENANT__","userId":"66666666-6666-4666-8666-666666666666",
         "traceId":"trace-1","idempotencyKey":"parse_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "payload":{"documentId":"__DOCUMENT__","sourceType":"TEXT","sourceSha256":"__SOURCE_HASH__",
         "normalizedTextSha256":"__TEXT_HASH__","chunkCount":1,"parserVersion":"1.0"}}
        """
        .replace("__EVENT__", EVENT)
        .replace("__TYPE__", type)
        .replace("__TENANT__", tenant)
        .replace("__DOCUMENT__", DOCUMENT)
        .replace("__SOURCE_HASH__", "a".repeat(64))
        .replace("__TEXT_HASH__", "b".repeat(64));
  }

  private static String embedding(String type, String tenant) {
    return """
        {"eventId":"__EVENT__","eventType":"__TYPE__","eventVersion":1,
         "timestamp":"2026-07-22T00:00:00Z","source":"python-engine-embedding",
         "tenantId":"__TENANT__","traceId":"trace-1","payload":{"jobId":"__EVENT__",
         "knowledgeBaseId":"__BASE__","documentId":"__DOCUMENT__","chunkCount":1,
         "embeddingModel":"fixture","modelVersion":"1.0","vectorDimension":8}}
        """
        .replace("__EVENT__", EVENT)
        .replace("__TYPE__", type)
        .replace("__TENANT__", tenant)
        .replace("__BASE__", BASE)
        .replace("__DOCUMENT__", DOCUMENT);
  }
}
