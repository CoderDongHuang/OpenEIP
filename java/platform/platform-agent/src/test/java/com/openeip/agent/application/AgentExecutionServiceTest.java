package com.openeip.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openeip.agent.api.dto.ExecuteAgentRequest;
import com.openeip.agent.shared.exception.AgentException;
import com.openeip.knowledge.application.KnowledgeBaseService;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentExecutionServiceTest {
  private static final String USER = "11111111-1111-4111-8111-111111111111";
  private static final String REQUEST = "22222222-2222-4222-8222-222222222222";
  private static final String BASE = "33333333-3333-4333-8333-333333333333";

  private KnowledgeBaseService knowledge;
  private AgentExecutionService service;

  @BeforeEach
  void setUp() {
    knowledge = mock(KnowledgeBaseService.class);
    service = new AgentExecutionService(knowledge);
  }

  @Test
  void exposesImmutableVersionedCatalog() {
    var catalog = service.catalog();
    assertThat(catalog).hasSize(1);
    assertThat(catalog.getFirst().agentId()).isEqualTo(AgentExecutionService.AGENT_ID);
    assertThat(catalog.getFirst().spiVersion()).isEqualTo("1.0");
    assertThat(catalog.getFirst().tools())
        .extracting(tool -> tool.name())
        .containsExactly("document.inspect", "knowledge.search");
    assertThatThrownBy(() -> catalog.getFirst().tools().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> catalog.getFirst().tools().getFirst().inputSchema().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void beginsBoundedInspectWithoutGrantingKnowledgeAuthority() {
    var context =
        service.begin(
            USER,
            REQUEST,
            AgentExecutionService.AGENT_ID,
            new ExecuteAgentRequest("inspect this", null, List.of("document.inspect"), null));

    assertThat(context.executionId()).isNotBlank();
    assertThat(context.requestId()).isEqualTo(REQUEST);
    assertThat(context.input()).isEqualTo("inspect this");
    assertThat(context.knowledgeBaseId()).isNull();
    assertThat(context.allowedTools()).containsExactly("document.inspect");
    assertThat(context.maxSteps()).isEqualTo(4);
    assertThatThrownBy(() -> context.allowedTools().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rechecksKnowledgeMembershipBeforeSearchExecution() {
    var context =
        service.begin(
            USER,
            REQUEST,
            AgentExecutionService.AGENT_ID,
            new ExecuteAgentRequest("search", BASE, List.of("knowledge.search"), 2));

    verify(knowledge).get(USER, BASE);
    assertThat(context.knowledgeBaseId()).isEqualTo(BASE);
    assertThat(context.maxSteps()).isEqualTo(2);

    when(knowledge.get(USER, BASE)).thenThrow(KnowledgeException.notFound());
    assertThatThrownBy(
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest("search", BASE, List.of("knowledge.search"), 2)))
        .isInstanceOf(AgentException.class)
        .extracting(error -> ((AgentException) error).getErrorCode())
        .isEqualTo("AGENT-N-001");
  }

  @Test
  void rejectsInvalidIdentityInputToolAuthorityAndLimits() {
    var inspect = new ExecuteAgentRequest("inspect", null, List.of("document.inspect"), 1);
    for (Runnable invalid :
        List.<Runnable>of(
            () -> service.begin("bad", REQUEST, AgentExecutionService.AGENT_ID, inspect),
            () -> service.begin(USER, "bad", AgentExecutionService.AGENT_ID, inspect),
            () -> service.begin(USER, REQUEST, "unknown.agent", inspect),
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest(
                        "bad\u0000input", null, List.of("document.inspect"), 1)),
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest("inspect", null, List.of("unknown.tool"), 1)),
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest(
                        "inspect", null, List.of("document.inspect", "document.inspect"), 1)),
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest("search", null, List.of("knowledge.search"), 1)),
            () ->
                service.begin(
                    USER,
                    REQUEST,
                    AgentExecutionService.AGENT_ID,
                    new ExecuteAgentRequest("inspect", null, List.of("document.inspect"), 9)))) {
      assertThatThrownBy(invalid::run).isInstanceOf(AgentException.class);
    }
  }
}
