package com.openeip.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.agent.api.dto.AgentMetadataResponse;
import com.openeip.agent.api.dto.ExecuteAgentRequest;
import com.openeip.agent.application.AgentExecutionService;
import com.openeip.agent.application.AgentExecutionService.ExecutionContext;
import com.openeip.agent.infrastructure.AgentStreamGateway;
import com.openeip.common.web.RequestIdFilter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class AgentControllerTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String REQUEST = "22222222-2222-4222-8222-222222222222";
  private static final String EXECUTION = "33333333-3333-4333-8333-333333333333";

  private AgentExecutionService executions;
  private AgentStreamGateway gateway;
  private AgentController controller;
  private TestingAuthenticationToken authentication;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    executions = mock(AgentExecutionService.class);
    gateway = mock(AgentStreamGateway.class);
    controller = new AgentController(executions, gateway);
    authentication = new TestingAuthenticationToken(USER, null, "ROLE_USER");
    request = new MockHttpServletRequest();
    request.setAttribute(RequestIdFilter.ATTRIBUTE, REQUEST);
  }

  @Test
  void returnsCatalogInStableRequestEnvelope() {
    when(executions.catalog())
        .thenReturn(
            List.of(
                new AgentMetadataResponse(
                    AgentExecutionService.AGENT_ID,
                    "Agent",
                    "bounded",
                    "1.0.0",
                    "1.0",
                    List.of())));

    var response = controller.catalog(request);
    assertThat(response.requestId()).isEqualTo(REQUEST);
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().getFirst().agentId()).isEqualTo(AgentExecutionService.AGENT_ID);
  }

  @Test
  void authorizesBeforeReturningRequiredSseHeaders() {
    ExecuteAgentRequest body =
        new ExecuteAgentRequest("inspect", null, List.of("document.inspect"), 1);
    ExecutionContext context =
        new ExecutionContext(
            EXECUTION,
            REQUEST,
            AgentExecutionService.AGENT_ID,
            "inspect",
            null,
            Set.of("document.inspect"),
            1);
    StreamingResponseBody stream = output -> output.write('x');
    when(executions.begin(USER, REQUEST, AgentExecutionService.AGENT_ID, body)).thenReturn(context);
    when(gateway.open(USER, context)).thenReturn(stream);

    var response = controller.stream(AgentExecutionService.AGENT_ID, body, authentication, request);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("text/event-stream");
    assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-cache, no-transform");
    assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
    assertThat(response.getBody()).isSameAs(stream);
    verify(executions).begin(USER, REQUEST, AgentExecutionService.AGENT_ID, body);
  }
}
