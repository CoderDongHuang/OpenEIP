package com.openeip.chat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.chat.api.dto.CreateChatSessionRequest;
import com.openeip.chat.api.dto.StreamChatMessageRequest;
import com.openeip.chat.application.ChatSessionService;
import com.openeip.chat.application.ChatSessionService.StreamContext;
import com.openeip.chat.domain.MessageRole;
import com.openeip.chat.domain.entity.ChatMessage;
import com.openeip.chat.domain.entity.ChatSession;
import com.openeip.chat.infrastructure.ChatStreamGateway;
import com.openeip.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class ChatControllerTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String BASE = "22222222-2222-4222-8222-222222222222";
  private static final String SESSION = "33333333-3333-4333-8333-333333333333";
  private static final String REQUEST = "44444444-4444-4444-8444-444444444444";
  private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

  private ChatSessionService sessions;
  private ChatStreamGateway gateway;
  private ChatController controller;
  private TestingAuthenticationToken authentication;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    sessions = mock(ChatSessionService.class);
    gateway = mock(ChatStreamGateway.class);
    controller = new ChatController(sessions, gateway);
    authentication = new TestingAuthenticationToken(USER, null, "ROLE_USER");
    request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST);
  }

  @Test
  void createsSessionAndMapsStableEnvelope() {
    ChatSession session = new ChatSession(SESSION, "default", USER, BASE, "Title", NOW);
    when(sessions.create(USER, BASE, "Title")).thenReturn(session);
    var response =
        controller.create(new CreateChatSessionRequest(BASE, "Title"), authentication, request);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().requestId()).isEqualTo(REQUEST);
    assertThat(response.getBody().data().sessionId()).isEqualTo(SESSION);
    assertThat(response.getBody().data().title()).isEqualTo("Title");
    assertThat(response.getBody().data().createdAt()).isEqualTo(NOW);
    assertThat(response.getBody().data().updatedAt()).isEqualTo(NOW);
  }

  @Test
  void returnsOrderedMessageHistoryWithoutInternalTenantOrOwnerFields() {
    ChatMessage message =
        new ChatMessage(
            "55555555-5555-4555-8555-555555555555",
            "default",
            SESSION,
            USER,
            0,
            MessageRole.USER,
            "question",
            NOW);
    when(sessions.history(USER, SESSION)).thenReturn(List.of(message));
    var response = controller.history(SESSION, authentication, request);

    assertThat(response.data()).hasSize(1);
    assertThat(response.data().getFirst().role()).isEqualTo("user");
    assertThat(response.data().getFirst().content()).isEqualTo("question");
    assertThat(response.data().getFirst().sequence()).isZero();
    assertThat(response.data().getFirst().createdAt()).isEqualTo(NOW);
    assertThat(message.getTenantId()).isEqualTo("default");
    assertThat(message.getSessionId()).isEqualTo(SESSION);
    assertThat(message.getOwnerId()).isEqualTo(USER);
  }

  @Test
  void beginsStreamBeforeReturningRequiredSseHeaders() {
    var context = new StreamContext(SESSION, BASE, REQUEST, "question", 5);
    StreamingResponseBody body = output -> output.write('x');
    when(sessions.begin(USER, SESSION, REQUEST, "question", 5)).thenReturn(context);
    when(gateway.open(USER, context)).thenReturn(body);

    var response =
        controller.stream(
            SESSION, new StreamChatMessageRequest("question", null), authentication, request);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("text/event-stream");
    assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-cache, no-transform");
    assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    assertThat(response.getBody()).isSameAs(body);
    verify(sessions).begin(USER, SESSION, REQUEST, "question", 5);
    assertThat(new StreamChatMessageRequest("question", 2).resolvedTopK()).isEqualTo(2);
  }
}
