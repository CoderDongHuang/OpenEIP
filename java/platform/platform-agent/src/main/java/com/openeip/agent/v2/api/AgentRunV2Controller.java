package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateRunRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.DecideApprovalRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.PageResponse;
import com.openeip.agent.v2.application.AgentRunService;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2")
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring collaborators are application-scoped and intentionally shared.")
public class AgentRunV2Controller {
  private final AgentRunService runs;
  private final ObjectMapper mapper;
  private final AgentPlatformApiSupport api;

  public AgentRunV2Controller(AgentRunService runs, ObjectMapper mapper) {
    this.runs = runs;
    this.mapper = mapper;
    this.api = new AgentPlatformApiSupport(mapper);
  }

  @PostMapping("/agents/{agentId}/runs")
  public ResponseEntity<ApiEnvelope<JsonNode>> create(
      @PathVariable String agentId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreateRunRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    JsonNode handles =
        mapper.valueToTree(body.resourceHandles() == null ? List.of() : body.resourceHandles());
    var value =
        runs.create(
            authentication.getName(),
            agentId,
            idempotencyKey,
            body.agentVersion(),
            body.input(),
            handles,
            body.budget());
    return response(202, value.revision(), value, request);
  }

  @GetMapping("/agent-runs")
  public ApiEnvelope<PageResponse> list(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        api.page(runs.list(authentication.getName(), status, limit)), RequestIdFilter.get(request));
  }

  @GetMapping("/agent-runs/{runId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> get(
      @PathVariable String runId, Authentication authentication, HttpServletRequest request) {
    var value = runs.get(authentication.getName(), runId);
    return response(200, value.revision(), value, request);
  }

  @GetMapping("/agent-runs/{runId}/events")
  public ApiEnvelope<PageResponse> events(
      @PathVariable String runId,
      @RequestParam(defaultValue = "0") @Min(0) long afterSequence,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        api.page(runs.events(authentication.getName(), runId, afterSequence, limit)),
        RequestIdFilter.get(request));
  }

  @PostMapping("/agent-runs/{runId}:cancel")
  public ResponseEntity<ApiEnvelope<JsonNode>> cancel(
      @PathVariable String runId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    return command(authentication, runId, "CANCEL", ifMatch, key, request);
  }

  @PostMapping("/agent-runs/{runId}:pause")
  public ResponseEntity<ApiEnvelope<JsonNode>> pause(
      @PathVariable String runId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    return command(authentication, runId, "PAUSE", ifMatch, key, request);
  }

  @PostMapping("/agent-runs/{runId}:resume")
  public ResponseEntity<ApiEnvelope<JsonNode>> resume(
      @PathVariable String runId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    return command(authentication, runId, "RESUME", ifMatch, key, request);
  }

  @PostMapping("/agent-runs/{runId}/steps/{stepId}:retry")
  public ResponseEntity<ApiEnvelope<JsonNode>> retry(
      @PathVariable String runId,
      @PathVariable String stepId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = runs.retryStep(authentication.getName(), runId, stepId, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  @PostMapping("/agent-runs/{runId}/approvals/{approvalId}:decide")
  public ResponseEntity<ApiEnvelope<JsonNode>> decide(
      @PathVariable String runId,
      @PathVariable String approvalId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody DecideApprovalRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        runs.decideApproval(
            authentication.getName(),
            runId,
            approvalId,
            body.decision(),
            body.reason(),
            ifMatch,
            key);
    return response(200, value.revision(), value, request);
  }

  private ResponseEntity<ApiEnvelope<JsonNode>> command(
      Authentication authentication,
      String runId,
      String command,
      String ifMatch,
      String key,
      HttpServletRequest request) {
    var value = runs.command(authentication.getName(), runId, command, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  private ResponseEntity<ApiEnvelope<JsonNode>> response(
      int status, long revision, Object value, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .eTag(Long.toString(revision))
        .body(ApiEnvelope.success(api.json(value), RequestIdFilter.get(request)));
  }
}
