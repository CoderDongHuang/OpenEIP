package com.openeip.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.knowledge.domain.MemberRole;
import com.openeip.knowledge.domain.ProcessingStatus;
import com.openeip.knowledge.domain.entity.KnowledgeBase;
import com.openeip.knowledge.domain.entity.KnowledgeBaseMember;
import com.openeip.knowledge.domain.entity.KnowledgeDocument;
import com.openeip.knowledge.domain.repository.KnowledgeBaseMemberRepository;
import com.openeip.knowledge.domain.repository.KnowledgeBaseRepository;
import com.openeip.knowledge.domain.repository.KnowledgeDocumentRepository;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String BASE = "22222222-2222-4222-8222-222222222222";
  private static final String DOCUMENT = "33333333-3333-4333-8333-333333333333";
  private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

  private KnowledgeBaseMemberRepository members;
  private KnowledgeDocumentRepository documents;
  private KnowledgeBaseService service;

  @BeforeEach
  void setUp() {
    KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    members = mock(KnowledgeBaseMemberRepository.class);
    documents = mock(KnowledgeDocumentRepository.class);
    when(bases.findByIdAndTenantIdAndDeletedAtIsNull(BASE, "default"))
        .thenReturn(
            Optional.of(new KnowledgeBase(BASE, "default", USER, "Engineering", "Runbooks", NOW)));
    service =
        new KnowledgeBaseService(
            bases,
            members,
            documents,
            mock(DocumentFileRepository.class),
            Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
  }

  @Test
  void viewerCannotStartDocumentProcessing() {
    when(members.findByTenantIdAndKnowledgeBaseIdAndUserId("default", BASE, USER))
        .thenReturn(Optional.of(member(MemberRole.VIEWER)));

    assertThatThrownBy(() -> service.getEditableDocument(USER, BASE, DOCUMENT))
        .isInstanceOf(KnowledgeException.class)
        .hasMessage("Insufficient knowledge permission");
  }

  @Test
  void editorCanResetFailedDocumentForRetry() {
    KnowledgeDocument document = document();
    document.fail("INGEST.STORAGE", NOW.plusSeconds(1));
    when(members.findByTenantIdAndKnowledgeBaseIdAndUserId("default", BASE, USER))
        .thenReturn(Optional.of(member(MemberRole.EDITOR)));
    when(documents.findByTenantIdAndKnowledgeBaseIdAndDocumentId("default", BASE, DOCUMENT))
        .thenReturn(Optional.of(document));

    KnowledgeDocument reset = service.resetDocumentForRetry(USER, BASE, DOCUMENT);

    assertThat(reset.getStatus()).isEqualTo(ProcessingStatus.PENDING_PARSE);
    assertThat(reset.getRetryCount()).isOne();
    assertThat(reset.getUpdatedAt()).isEqualTo(NOW.plusSeconds(10));
  }

  private static KnowledgeBaseMember member(MemberRole role) {
    return new KnowledgeBaseMember(
        "44444444-4444-4444-8444-444444444444", "default", BASE, USER, role, NOW);
  }

  private static KnowledgeDocument document() {
    return new KnowledgeDocument(
        "55555555-5555-4555-8555-555555555555", "default", BASE, DOCUMENT, NOW);
  }
}
