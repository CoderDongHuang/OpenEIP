package com.openeip.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.entity.ProcessedKnowledgeEvent;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.openeip.knowledge.domain.repository.ProcessedKnowledgeEventRepository;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeEventServiceTest {
  private static final String EVENT = "11111111-1111-1111-1111-111111111111";
  private static final String BASE = "22222222-2222-2222-2222-222222222222";
  private static final String DOCUMENT = "33333333-3333-3333-3333-333333333333";
  private static final String FINGERPRINT = "a".repeat(64);
  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

  @Mock KnowledgeDocumentRepository documents;
  @Mock ProcessedKnowledgeEventRepository events;
  private KnowledgeEventService service;
  private KnowledgeDocument document;

  @BeforeEach
  void setUp() {
    service = new KnowledgeEventService(documents, events, Clock.fixed(NOW, ZoneOffset.UTC));
    document = new KnowledgeDocument("doc-row", "default", BASE, DOCUMENT, NOW.minusSeconds(1));
  }

  @Test
  void parsedEventAdvancesAllAssociationsAndPersistsIdempotency() {
    KnowledgeDocument second =
        new KnowledgeDocument(
            "doc-row-2", "default", "44444444-4444-4444-4444-444444444444", DOCUMENT, NOW);
    when(events.findByTenantIdAndEventId("default", EVENT)).thenReturn(Optional.empty());
    when(documents.findAllByTenantIdAndDocumentId("default", DOCUMENT))
        .thenReturn(List.of(document, second));

    var result = service.processParsed(EVENT, "default", DOCUMENT, FINGERPRINT);

    assertThat(result.outcome()).isEqualTo("ADVANCED");
    assertThat(result.duplicate()).isFalse();
    assertThat(document.getStatus()).isEqualTo(ProcessingStatus.PARSED);
    assertThat(second.getStatus()).isEqualTo(ProcessingStatus.PARSED);
    verify(events).save(any(ProcessedKnowledgeEvent.class));
  }

  @Test
  void exactReplayReturnsStoredOutcomeWithoutMutation() {
    ProcessedKnowledgeEvent existing =
        new ProcessedKnowledgeEvent(
            "row",
            "default",
            EVENT,
            KnowledgeEventService.PARSED,
            DOCUMENT,
            FINGERPRINT,
            "ADVANCED",
            NOW);
    when(events.findByTenantIdAndEventId("default", EVENT)).thenReturn(Optional.of(existing));

    var result = service.processParsed(EVENT, "default", DOCUMENT, FINGERPRINT);

    assertThat(result).isEqualTo(new KnowledgeEventService.EventOutcome("ADVANCED", true));
    verify(documents, never()).findAllByTenantIdAndDocumentId(any(), any());
  }

  @Test
  void eventIdCollisionAndUnknownDocumentAreRejected() {
    ProcessedKnowledgeEvent existing =
        new ProcessedKnowledgeEvent(
            "row",
            "default",
            EVENT,
            KnowledgeEventService.PARSED,
            DOCUMENT,
            FINGERPRINT,
            "ADVANCED",
            NOW);
    when(events.findByTenantIdAndEventId("default", EVENT)).thenReturn(Optional.of(existing));
    assertThatThrownBy(() -> service.processParsed(EVENT, "default", DOCUMENT, "b".repeat(64)))
        .isInstanceOf(KnowledgeException.class)
        .hasMessageContaining("collision");

    when(events.findByTenantIdAndEventId("default", EVENT)).thenReturn(Optional.empty());
    when(documents.findAllByTenantIdAndDocumentId("default", DOCUMENT)).thenReturn(List.of());
    assertThatThrownBy(() -> service.processParsed(EVENT, "default", DOCUMENT, FINGERPRINT))
        .isInstanceOf(KnowledgeException.class);
  }

  @Test
  void schedulesAndCompletesEmbeddingWithoutRegressing() {
    when(documents.findByTenantIdAndKnowledgeBaseIdAndDocumentId("default", BASE, DOCUMENT))
        .thenReturn(Optional.of(document));
    document.transitionTo(ProcessingStatus.PARSED, NOW);
    assertThat(service.scheduleEmbedding("default", BASE, DOCUMENT)).isTrue();

    when(events.findByTenantIdAndEventId("default", EVENT)).thenReturn(Optional.empty());
    var result = service.processEmbeddingCompleted(EVENT, "default", BASE, DOCUMENT, FINGERPRINT);
    assertThat(result.outcome()).isEqualTo("ADVANCED");
    assertThat(document.getStatus()).isEqualTo(ProcessingStatus.READY);
  }

  @Test
  void rejectsMalformedEnvelopeAndSanitizesFailureState() {
    assertThatThrownBy(() -> service.processParsed("bad", "default", DOCUMENT, FINGERPRINT))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> service.processParsed(EVENT, "", DOCUMENT, FINGERPRINT))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> service.processParsed(EVENT, "default", DOCUMENT, "bad"))
        .isInstanceOf(KnowledgeException.class);

    when(documents.findByTenantIdAndKnowledgeBaseIdAndDocumentId("default", BASE, DOCUMENT))
        .thenReturn(Optional.of(document));
    service.markFailed("default", BASE, DOCUMENT, "PARSE_TIMEOUT");
    assertThat(document.getFailureCode()).isEqualTo("PARSE_TIMEOUT");
  }
}
