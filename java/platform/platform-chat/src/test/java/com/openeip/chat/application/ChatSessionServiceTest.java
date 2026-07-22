package com.openeip.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.chat.domain.MessageRole;
import com.openeip.chat.domain.entity.ChatMessage;
import com.openeip.chat.domain.entity.ChatSession;
import com.openeip.chat.domain.repository.ChatMessageRepository;
import com.openeip.chat.domain.repository.ChatSessionRepository;
import com.openeip.chat.shared.exception.ChatException;
import com.openeip.knowledge.application.KnowledgeBaseService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatSessionServiceTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String OTHER = "22222222-2222-4222-8222-222222222222";
  private static final String BASE = "33333333-3333-4333-8333-333333333333";
  private static final String SESSION = "44444444-4444-4444-8444-444444444444";
  private static final String REQUEST = "55555555-5555-4555-8555-555555555555";
  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

  private ChatSessionRepository sessions;
  private ChatMessageRepository messages;
  private KnowledgeBaseService knowledge;
  private ChatSessionService service;

  @BeforeEach
  void setUp() {
    sessions = mock(ChatSessionRepository.class);
    messages = mock(ChatMessageRepository.class);
    knowledge = mock(KnowledgeBaseService.class);
    service =
        new ChatSessionService(sessions, messages, knowledge, Clock.fixed(NOW, ZoneOffset.UTC));
    when(sessions.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void createRechecksKnowledgeMembershipAndAppliesBoundedDefaultTitle() {
    ChatSession created = service.create(USER, BASE, "  ");
    assertThat(created.getOwnerId()).isEqualTo(USER);
    assertThat(created.getKnowledgeBaseId()).isEqualTo(BASE);
    assertThat(created.getTitle()).isEqualTo("New conversation");
    verify(knowledge).get(USER, BASE);

    assertThatThrownBy(() -> service.create(USER, BASE, "x".repeat(121)))
        .isInstanceOf(ChatException.class);
  }

  @Test
  void historyUsesOwnerScopeAndNeverQueriesMessagesForAnIdor() {
    when(sessions.findByIdAndTenantIdAndOwnerId(SESSION, "default", OTHER))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.history(OTHER, SESSION))
        .isInstanceOf(ChatException.class)
        .hasMessage("Chat session not found");
    verify(messages, never())
        .findAllByTenantIdAndSessionIdAndOwnerIdOrderByMessageIndexAsc(any(), any(), any());

    ChatSession session = session();
    when(sessions.findByIdAndTenantIdAndOwnerId(SESSION, "default", USER))
        .thenReturn(Optional.of(session));
    when(messages.findAllByTenantIdAndSessionIdAndOwnerIdOrderByMessageIndexAsc(
            "default", SESSION, USER))
        .thenReturn(List.of());
    assertThat(service.history(USER, SESSION)).isEmpty();
  }

  @Test
  void beginRechecksMembershipPersistsUserMessageAndRejectsConcurrentStream() {
    ChatSession session = session();
    when(sessions.findOwnedForUpdate(SESSION, "default", USER)).thenReturn(Optional.of(session));

    var context = service.begin(USER, SESSION, REQUEST, "question", 5);
    assertThat(context.knowledgeBaseId()).isEqualTo(BASE);
    assertThat(session.getActiveRequestId()).isEqualTo(REQUEST);
    verify(knowledge).get(USER, BASE);
    ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
    verify(messages).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(MessageRole.USER);
    assertThat(saved.getValue().getContent()).isEqualTo("question");
    assertThat(saved.getValue().getMessageIndex()).isZero();

    assertThatThrownBy(() -> service.begin(USER, SESSION, REQUEST, "again", 5))
        .isInstanceOf(ChatException.class)
        .hasMessageContaining("already streaming");
  }

  @Test
  void completePersistsOnlyValidAssistantForMatchingLeaseAndCancelIsIdempotent() {
    ChatSession session = session();
    session.begin(REQUEST, NOW);
    when(sessions.findOwnedForUpdate(SESSION, "default", USER)).thenReturn(Optional.of(session));

    service.complete(USER, SESSION, REQUEST, "answer");
    ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
    verify(messages).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(MessageRole.ASSISTANT);
    assertThat(saved.getValue().getMessageIndex()).isEqualTo(1);
    assertThat(session.getActiveRequestId()).isNull();

    service.cancel(USER, SESSION, REQUEST);
    assertThat(session.getActiveRequestId()).isNull();
    assertThatThrownBy(() -> service.complete(USER, SESSION, REQUEST, "answer"))
        .isInstanceOf(ChatException.class);
  }

  @Test
  void beginRejectsInvalidIdentifiersMessageAndTopKBeforePersistence() {
    assertThatThrownBy(() -> service.begin(USER, "bad", REQUEST, "message", 5))
        .isInstanceOf(ChatException.class);
    assertThatThrownBy(() -> service.begin(USER, SESSION, "bad", "message", 5))
        .isInstanceOf(ChatException.class);
    assertThatThrownBy(() -> service.begin(USER, SESSION, REQUEST, "bad\u0000message", 5))
        .isInstanceOf(ChatException.class);
    assertThatThrownBy(() -> service.begin(USER, SESSION, REQUEST, "message", 21))
        .isInstanceOf(ChatException.class);
  }

  private static ChatSession session() {
    return new ChatSession(SESSION, "default", USER, BASE, "Session", NOW);
  }
}
