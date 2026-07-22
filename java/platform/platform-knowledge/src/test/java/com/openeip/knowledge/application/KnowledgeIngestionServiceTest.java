package com.openeip.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.document.application.service.DocumentFileService;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.ParsedChunk;
import com.openeip.knowledge.infrastructure.ingestion.KnowledgeIngestionGateway.ParsedDocument;
import com.openeip.knowledge.shared.exception.KnowledgeIngestionException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeIngestionServiceTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String BASE = "22222222-2222-4222-8222-222222222222";
  private static final String DOCUMENT = "33333333-3333-4333-8333-333333333333";
  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
  private static final byte[] CONTENT = "grounded source".getBytes(StandardCharsets.UTF_8);

  private KnowledgeBaseService bases;
  private KnowledgeEventService events;
  private DocumentFileService files;
  private KnowledgeIngestionGateway gateway;
  private KnowledgeIngestionService service;

  @BeforeEach
  void setUp() {
    bases = mock(KnowledgeBaseService.class);
    events = mock(KnowledgeEventService.class);
    files = mock(DocumentFileService.class);
    gateway = mock(KnowledgeIngestionGateway.class);
    service = new KnowledgeIngestionService(bases, events, Optional.of(files), gateway);
  }

  @Test
  void authorizesReadsValidatesDigestAndTransitionsToReady() {
    KnowledgeDocument pending = document();
    KnowledgeDocument ready = document();
    ready.transitionTo(ProcessingStatus.PARSED, NOW);
    ready.transitionTo(ProcessingStatus.PENDING_EMBEDDING, NOW);
    ready.transitionTo(ProcessingStatus.READY, NOW);
    when(bases.getDocument(USER, BASE, DOCUMENT)).thenReturn(pending, ready);
    when(files.open(USER, false, DOCUMENT))
        .thenReturn(
            new DocumentFileService.Download(
                file(sha256(CONTENT)), new ByteArrayInputStream(CONTENT)));
    ParsedDocument parsed =
        new ParsedDocument(
            "text",
            "a".repeat(64),
            List.of(new ParsedChunk("chk_" + "b".repeat(32), "source", "c".repeat(64))));
    when(gateway.parse(USER, DOCUMENT, "text/plain", CONTENT)).thenReturn(parsed);
    when(gateway.embed(USER, BASE, DOCUMENT, parsed.chunks())).thenReturn(1);

    var result = service.process(USER, false, BASE, DOCUMENT);

    assertThat(result.document().getStatus()).isEqualTo(ProcessingStatus.READY);
    assertThat(result.sourceType()).isEqualTo("text");
    assertThat(result.chunkCount()).isEqualTo(1);
    assertThat(result.vectorCount()).isEqualTo(1);
    verify(events).scheduleEmbedding("default", BASE, DOCUMENT);
    verify(events)
        .processEmbeddingCompleted(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void rejectsStoredContentDigestMismatchAndMarksStableFailure() {
    when(bases.getDocument(USER, BASE, DOCUMENT)).thenReturn(document());
    when(files.open(USER, false, DOCUMENT))
        .thenReturn(
            new DocumentFileService.Download(
                file("0".repeat(64)), new ByteArrayInputStream(CONTENT)));

    assertThatThrownBy(() -> service.process(USER, false, BASE, DOCUMENT))
        .isInstanceOf(KnowledgeIngestionException.class);

    verify(gateway, never())
        .parse(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    verify(events).markFailed("default", BASE, DOCUMENT, "INGEST.STORAGE");
  }

  private static KnowledgeDocument document() {
    return new KnowledgeDocument(
        "44444444-4444-4444-8444-444444444444", "default", BASE, DOCUMENT, NOW);
  }

  private static DocumentFile file(String digest) {
    return new DocumentFile(
        DOCUMENT,
        "default",
        USER,
        "source.txt",
        "33/source",
        "text/plain",
        CONTENT.length,
        digest,
        NOW);
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
