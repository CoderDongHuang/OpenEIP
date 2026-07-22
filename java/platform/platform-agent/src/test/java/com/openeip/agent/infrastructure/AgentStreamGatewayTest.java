package com.openeip.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.application.AgentExecutionService;
import com.openeip.agent.application.AgentExecutionService.ExecutionContext;
import com.openeip.agent.shared.exception.AgentException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentStreamGatewayTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String REQUEST = "22222222-2222-4222-8222-222222222222";
  private static final String EXECUTION = "33333333-3333-4333-8333-333333333333";
  private static final String BASE = "44444444-4444-4444-8444-444444444444";
  private static final String TENANT = "55555555-5555-4555-8555-555555555555";
  private static final String CALL = "66666666-6666-4666-8666-666666666666";

  private HttpServer server;
  private AtomicReference<String> response;
  private AtomicReference<String> receivedBody;
  private AtomicReference<String> receivedToken;
  private AtomicReference<String> receivedUser;
  private AtomicReference<String> receivedExecution;
  private AtomicReference<String> receivedRequest;
  private AtomicInteger status;

  @BeforeEach
  void setUp() throws IOException {
    response = new AtomicReference<>();
    receivedBody = new AtomicReference<>();
    receivedToken = new AtomicReference<>();
    receivedUser = new AtomicReference<>();
    receivedExecution = new AtomicReference<>();
    receivedRequest = new AtomicReference<>();
    status = new AtomicInteger(200);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/v1/internal/agents/" + AgentExecutionService.AGENT_ID + "/executions:stream",
        exchange -> {
          receivedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          receivedToken.set(exchange.getRequestHeaders().getFirst("X-OpenEIP-Internal-Token"));
          receivedUser.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
          receivedExecution.set(exchange.getRequestHeaders().getFirst("X-Execution-Id"));
          receivedRequest.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(status.get(), body.length);
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
  void forwardsStrictLifecycleAndCanonicalIdentity() throws Exception {
    response.set(
        started()
            + toolStarted(1, CALL, "document.inspect", 1)
            + toolCompleted(2, CALL, "document.inspect", 1)
            + answer(3, "safe text")
            + completed(4, 1));
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    gateway("internal-token")
        .open(USER, context(BASE, Set.of("document.inspect"), 4))
        .writeTo(output);

    String result = output.toString(StandardCharsets.UTF_8);
    assertThat(result)
        .contains(
            "event: execution.started",
            "event: tool.started",
            "event: tool.completed",
            "event: answer.delta",
            "event: execution.completed")
        .doesNotContain("event: execution.error");
    assertThat(result.lines().filter(line -> line.startsWith("data: "))).hasSize(5);
    assertThat(receivedToken.get()).isEqualTo("internal-token");
    assertThat(receivedUser.get()).isEqualTo(USER);
    assertThat(receivedExecution.get()).isEqualTo(EXECUTION);
    assertThat(receivedRequest.get()).isEqualTo(REQUEST);
    assertThat(receivedBody.get())
        .contains("\"input\":\"inspect\"")
        .contains("\"knowledgeBaseId\":\"" + BASE + "\"")
        .contains("\"maxSteps\":4");
  }

  @Test
  void omitsAbsentKnowledgeBaseFromInternalRequest() throws Exception {
    response.set(started() + answer(1, "safe") + completed(2, 0));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    gateway("token").open(USER, context(null, Set.of("document.inspect"), 1)).writeTo(output);
    assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: execution.completed");
    assertThat(receivedBody.get()).doesNotContain("knowledgeBaseId");
  }

  @Test
  void rejectsInvalidIdentitySchemaOrderAndToolTransitions() throws Exception {
    String otherCall = "77777777-7777-4777-8777-777777777777";
    List<String> invalidStreams =
        List.of(
            "event: unknown\ndata: {}\n\n",
            frame(
                "execution.started",
                0,
                "\"agentId\":\"" + AgentExecutionService.AGENT_ID + "\",\"extra\":true"),
            started().replace(REQUEST, "99999999-9999-4999-8999-999999999999"),
            frame("execution.started", 1, "\"agentId\":\"" + AgentExecutionService.AGENT_ID + "\""),
            started() + toolStarted(1, CALL, "unknown.tool", 1),
            started()
                + toolStarted(1, CALL, "document.inspect", 1)
                + toolStarted(2, otherCall, "document.inspect", 2),
            started()
                + toolStarted(1, CALL, "document.inspect", 1)
                + toolCompleted(2, CALL, "knowledge.search", 1),
            started()
                + toolStarted(1, CALL, "document.inspect", 1)
                + toolCompleted(2, CALL, "document.inspect", 2),
            started()
                + toolStarted(1, CALL, "document.inspect", 1)
                + toolCompleted(2, CALL, "document.inspect", 1)
                + toolStarted(3, CALL, "document.inspect", 2),
            started() + toolStarted(1, CALL, "document.inspect", 1) + answer(2, "unclosed"),
            started() + answer(1, "answer") + completed(2, 1),
            started() + "event: answer.delta\ndata: not-json\n\n");

    for (String invalid : invalidStreams) {
      response.set(invalid);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      gateway("token")
          .open(USER, context(BASE, Set.of("document.inspect", "knowledge.search"), 4))
          .writeTo(output);
      assertThat(output.toString(StandardCharsets.UTF_8))
          .contains("event: execution.error", "AGENT-S-001")
          .doesNotContain("unknown.tool", "not-json", "unclosed");
    }
  }

  @Test
  void sanitizesPythonErrorsAndNonSuccessfulResponses() throws Exception {
    response.set(
        started()
            + frame(
                "execution.error",
                1,
                "\"code\":\"provider-secret\",\"message\":\"prompt credential traceback\""));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    gateway("token").open(USER, context(null, Set.of("document.inspect"), 1)).writeTo(output);
    assertThat(output.toString(StandardCharsets.UTF_8))
        .contains("AGENT-S-001", "Agent execution failed")
        .doesNotContain("provider-secret", "prompt", "credential", "traceback");

    status.set(503);
    response.set("upstream credential");
    output.reset();
    gateway("token").open(USER, context(null, Set.of("document.inspect"), 1)).writeTo(output);
    assertThat(output.toString(StandardCharsets.UTF_8))
        .contains("AGENT-S-001")
        .doesNotContain("credential");
  }

  @Test
  void failsClosedWithoutInternalTokenAndPropagatesClientDisconnect() throws Exception {
    assertThatThrownBy(() -> gateway("").open(USER, context(null, Set.of("document.inspect"), 1)))
        .isInstanceOf(AgentException.class);

    response.set(started() + answer(1, "safe") + completed(2, 0));
    try (OutputStream disconnected =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            throw new IOException("client disconnected");
          }
        }) {
      assertThatThrownBy(
              () ->
                  gateway("token")
                      .open(USER, context(null, Set.of("document.inspect"), 1))
                      .writeTo(disconnected))
          .isInstanceOf(IOException.class);
    }
  }

  private AgentStreamGateway gateway(String token) {
    return new AgentStreamGateway(
        new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(), token, TENANT);
  }

  private static ExecutionContext context(
      String knowledgeBaseId, Set<String> allowedTools, int maxSteps) {
    return new ExecutionContext(
        EXECUTION,
        REQUEST,
        AgentExecutionService.AGENT_ID,
        "inspect",
        knowledgeBaseId,
        allowedTools,
        maxSteps);
  }

  private static String started() {
    return frame("execution.started", 0, "\"agentId\":\"" + AgentExecutionService.AGENT_ID + "\"");
  }

  private static String toolStarted(int sequence, String callId, String name, int step) {
    return frame(
        "tool.started",
        sequence,
        "\"toolCallId\":\"" + callId + "\",\"toolName\":\"" + name + "\",\"step\":" + step);
  }

  private static String toolCompleted(int sequence, String callId, String name, int step) {
    return frame(
        "tool.completed",
        sequence,
        "\"toolCallId\":\""
            + callId
            + "\",\"toolName\":\""
            + name
            + "\",\"step\":"
            + step
            + ",\"durationMs\":1.5,\"resultChars\":10");
  }

  private static String answer(int sequence, String text) {
    return frame("answer.delta", sequence, "\"text\":\"" + text + "\"");
  }

  private static String completed(int sequence, int steps) {
    return frame("execution.completed", sequence, "\"finishReason\":\"stop\",\"steps\":" + steps);
  }

  private static String frame(String event, int sequence, String fields) {
    return "event: "
        + event
        + "\ndata: {\"requestId\":\""
        + REQUEST
        + "\",\"executionId\":\""
        + EXECUTION
        + "\",\"sequence\":"
        + sequence
        + ","
        + fields
        + "}\n\n";
  }
}
