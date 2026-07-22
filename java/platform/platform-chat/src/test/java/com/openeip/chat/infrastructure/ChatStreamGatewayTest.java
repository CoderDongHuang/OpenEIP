package com.openeip.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.chat.application.ChatSessionService;
import com.openeip.chat.application.ChatSessionService.StreamContext;
import com.openeip.chat.shared.exception.ChatException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatStreamGatewayTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String SESSION = "22222222-2222-4222-8222-222222222222";
  private static final String REQUEST = "33333333-3333-4333-8333-333333333333";
  private static final String BASE = "44444444-4444-4444-8444-444444444444";
  private static final String TENANT = "55555555-5555-4555-8555-555555555555";

  private ChatSessionService sessions;
  private HttpServer server;
  private AtomicReference<String> response;
  private AtomicReference<String> receivedBody;

  @BeforeEach
  void setUp() throws IOException {
    sessions = mock(ChatSessionService.class);
    response = new AtomicReference<>();
    receivedBody = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/internal/chat/messages:stream",
        exchange -> {
          receivedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void validatesAndForwardsTokensThenPersistsCompleteAssistant() throws Exception {
    response.set(token(0, "safe\\ntext") + done());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    gateway("token").open(USER, context()).writeTo(output);

    String result = output.toString(StandardCharsets.UTF_8);
    assertThat(result).contains("event: token", "event: done").doesNotContain("event: error");
    assertThat(result.lines().filter(line -> line.startsWith("data: "))).hasSize(2);
    verify(sessions).complete(USER, SESSION, REQUEST, "safe\ntext");
    assertThat(receivedBody.get()).contains("\"knowledgeBaseId\":\"" + BASE + "\"");
  }

  @Test
  void rejectsUnknownOutOfOrderOrIdentityMismatchedEventsAndCancelsLease() throws Exception {
    for (String invalid :
        new String[] {
          "event: message\ndata: {}\n\n",
          token(1, "out of order") + done(),
          "event: token\ndata: {\"requestId\":\"wrong\",\"sessionId\":\""
              + SESSION
              + "\",\"sequence\":0,\"token\":\"x\"}\n\n"
        }) {
      response.set(invalid);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      gateway("token").open(USER, context()).writeTo(output);
      assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: error");
    }
    verify(sessions, org.mockito.Mockito.atLeast(3)).cancel(USER, SESSION, REQUEST);
  }

  @Test
  void rejectsMalformedOrDuplicateCitations() throws Exception {
    String valid =
        "{\"documentId\":\""
            + USER
            + "\",\"chunkId\":\"chk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
            + "\"sourceSha256\":\""
            + "a".repeat(64)
            + "\",\"score\":0.9}";
    for (String invalid :
        new String[] {
          valid.replace("chk_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "bad"),
          valid.replace("\"score\":0.9", "\"score\":2"),
          valid.substring(0, valid.length() - 1) + ",\"unexpected\":true}",
          valid + "," + valid
        }) {
      response.set(token(0, "answer") + doneWithCitations(invalid));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      gateway("token").open(USER, context()).writeTo(output);
      assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: error");
    }
    verify(sessions, org.mockito.Mockito.atLeast(4)).cancel(USER, SESSION, REQUEST);
  }

  @Test
  void mapsUpstreamErrorAndDisconnectWithoutPersistingPartialAssistant() throws Exception {
    response.set(
        "event: error\ndata: {\"requestId\":\""
            + REQUEST
            + "\",\"sessionId\":\""
            + SESSION
            + "\",\"code\":\"secret\",\"message\":\"credential\"}\n\n");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    gateway("token").open(USER, context()).writeTo(output);
    assertThat(output.toString(StandardCharsets.UTF_8))
        .contains("CHAT-S-001")
        .doesNotContain("secret", "credential");
    verify(sessions).cancel(USER, SESSION, REQUEST);

    response.set(token(0, "partial") + done());
    try (OutputStream disconnected =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            throw new IOException("client disconnected");
          }
        }) {
      assertThatThrownBy(() -> gateway("token").open(USER, context()).writeTo(disconnected))
          .isInstanceOf(IOException.class);
    }
  }

  @Test
  void failsClosedBeforeOpeningWhenInternalCredentialIsMissing() {
    assertThatThrownBy(() -> gateway("").open(USER, context())).isInstanceOf(ChatException.class);
    verify(sessions).cancel(USER, SESSION, REQUEST);
  }

  private ChatStreamGateway gateway(String token) {
    return new ChatStreamGateway(
        sessions,
        new ObjectMapper(),
        "http://127.0.0.1:" + server.getAddress().getPort(),
        token,
        TENANT);
  }

  private static StreamContext context() {
    return new StreamContext(SESSION, BASE, REQUEST, "question", 5);
  }

  private static String token(int sequence, String value) {
    return "event: token\ndata: {\"requestId\":\""
        + REQUEST
        + "\",\"sessionId\":\""
        + SESSION
        + "\",\"sequence\":"
        + sequence
        + ",\"token\":\""
        + value
        + "\"}\n\n";
  }

  private static String done() {
    return doneWithCitations("");
  }

  private static String doneWithCitations(String citations) {
    return "event: done\ndata: {\"requestId\":\""
        + REQUEST
        + "\",\"sessionId\":\""
        + SESSION
        + "\",\"finishReason\":\"stop\",\"citations\":["
        + citations
        + "]}\n\n";
  }
}
