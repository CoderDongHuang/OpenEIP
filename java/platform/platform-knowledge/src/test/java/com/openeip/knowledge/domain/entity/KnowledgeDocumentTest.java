package com.openeip.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentTest {
  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

  @Test
  void followsTheOnlySuccessfulLifecycleAndTreatsSameStateAsIdempotent() {
    KnowledgeDocument document = document();
    assertThat(document.getStatus()).isEqualTo(ProcessingStatus.PENDING_PARSE);
    assertThat(document.transitionTo(ProcessingStatus.PENDING_PARSE, NOW)).isFalse();
    assertThat(document.transitionTo(ProcessingStatus.PARSED, NOW.plusSeconds(1))).isTrue();
    assertThat(document.isAtOrBeyondParsed()).isTrue();
    assertThat(document.transitionTo(ProcessingStatus.PENDING_EMBEDDING, NOW.plusSeconds(2)))
        .isTrue();
    assertThat(document.transitionTo(ProcessingStatus.READY, NOW.plusSeconds(3))).isTrue();
    assertThat(document.getUpdatedAt()).isEqualTo(NOW.plusSeconds(3));
    assertThat(document.getFailureCode()).isNull();
  }

  @Test
  void rejectsSkippedAndTerminalTransitions() {
    KnowledgeDocument document = document();
    assertThatThrownBy(() -> document.transitionTo(ProcessingStatus.READY, NOW))
        .isInstanceOf(KnowledgeException.class)
        .hasMessageContaining("PENDING_PARSE -> READY");
    document.transitionTo(ProcessingStatus.PARSED, NOW);
    assertThatThrownBy(() -> document.transitionTo(ProcessingStatus.READY, NOW))
        .isInstanceOf(KnowledgeException.class);
    document.transitionTo(ProcessingStatus.PENDING_EMBEDDING, NOW);
    document.transitionTo(ProcessingStatus.READY, NOW);
    assertThatThrownBy(() -> document.transitionTo(ProcessingStatus.FAILED, NOW))
        .isInstanceOf(KnowledgeException.class);
  }

  @Test
  void boundsFailureDiagnosticsAndRetryCount() {
    KnowledgeDocument document = document();
    assertThatThrownBy(() -> document.fail("contains secret text", NOW))
        .isInstanceOf(KnowledgeException.class);
    for (int attempt = 0; attempt < 5; attempt++) {
      document.fail("OCR_TIMEOUT", NOW.plusSeconds(attempt));
    }
    assertThat(document.getStatus()).isEqualTo(ProcessingStatus.FAILED);
    assertThat(document.getFailureCode()).isEqualTo("OCR_TIMEOUT");
    assertThat(document.getRetryCount()).isEqualTo(3);
    assertThat(document.isAtOrBeyondParsed()).isFalse();
  }

  @Test
  void exposesStableIdentityFields() {
    KnowledgeDocument document = document();
    assertThat(document.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(document.getTenantId()).isEqualTo("default");
    assertThat(document.getKnowledgeBaseId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    assertThat(document.getDocumentId()).isEqualTo("33333333-3333-3333-3333-333333333333");
    assertThat(document.getCreatedAt()).isEqualTo(NOW);
  }

  private static KnowledgeDocument document() {
    return new KnowledgeDocument(
        "11111111-1111-1111-1111-111111111111",
        "default",
        "22222222-2222-2222-2222-222222222222",
        "33333333-3333-3333-3333-333333333333",
        NOW);
  }
}
