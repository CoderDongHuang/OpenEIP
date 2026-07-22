package com.openeip.agent.api;

import com.openeip.agent.api.dto.AgentMetadataResponse;
import com.openeip.agent.api.dto.ExecuteAgentRequest;
import com.openeip.agent.application.AgentExecutionService;
import com.openeip.agent.infrastructure.AgentStreamGateway;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
  private final AgentExecutionService executions;
  private final AgentStreamGateway gateway;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Injected collaborators are application-scoped services.")
  public AgentController(AgentExecutionService executions, AgentStreamGateway gateway) {
    this.executions = executions;
    this.gateway = gateway;
  }

  @GetMapping
  public ApiEnvelope<List<AgentMetadataResponse>> catalog(HttpServletRequest request) {
    return ApiEnvelope.success(executions.catalog(), RequestIdFilter.get(request));
  }

  @PostMapping(value = "/{agentId}/executions:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<StreamingResponseBody> stream(
      @PathVariable("agentId") String agentId,
      @Valid @RequestBody ExecuteAgentRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    String userId = authentication.getName();
    var context = executions.begin(userId, RequestIdFilter.get(request), agentId, body);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
        .header("X-Accel-Buffering", "no")
        .body(gateway.open(userId, context));
  }
}
