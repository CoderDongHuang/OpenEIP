package com.openeip.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.workflow.api.WorkflowDtos.ApprovalDecisionRequest;
import com.openeip.workflow.api.WorkflowDtos.EventResponse;
import com.openeip.workflow.api.WorkflowDtos.ExecutionResponse;
import com.openeip.workflow.application.WorkflowExecutionService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/v1")
public class WorkflowExecutionController {
  private final WorkflowExecutionService executions;
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowExecutionController(WorkflowExecutionService executions, ObjectMapper mapper) {
    this.executions = executions;
    this.mapper = mapper;
  }

  @GetMapping("/workflow-executions/{executionId}")
  public ApiEnvelope<ExecutionResponse> get(
      @PathVariable("executionId") String executionId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        ExecutionResponse.from(executions.get(authentication.getName(), executionId)),
        RequestIdFilter.get(request));
  }

  @PostMapping("/workflow-executions/{executionId}/cancel")
  public ResponseEntity<ApiEnvelope<ExecutionResponse>> cancel(
      @PathVariable("executionId") String executionId,
      Authentication authentication,
      HttpServletRequest request) {
    ExecutionResponse data =
        ExecutionResponse.from(executions.cancel(authentication.getName(), executionId));
    return ResponseEntity.accepted().body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @PostMapping("/workflow-executions/{executionId}/nodes/{nodeId}/retry")
  public ResponseEntity<ApiEnvelope<ExecutionResponse>> retry(
      @PathVariable("executionId") String executionId,
      @PathVariable("nodeId") String nodeId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    ExecutionResponse data =
        ExecutionResponse.from(
            executions.retry(authentication.getName(), executionId, nodeId, idempotencyKey));
    return ResponseEntity.accepted().body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping("/workflow-executions/{executionId}/events")
  public ApiEnvelope<List<EventResponse>> events(
      @PathVariable("executionId") String executionId,
      @RequestParam(name = "after", defaultValue = "0") @Min(0) long after,
      Authentication authentication,
      HttpServletRequest request) {
    List<EventResponse> data =
        executions.events(authentication.getName(), executionId, after).stream()
            .map(value -> EventResponse.from(value, mapper))
            .toList();
    return ApiEnvelope.success(data, RequestIdFilter.get(request));
  }

  @GetMapping(
      value = "/workflow-executions/{executionId}/events:stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable("executionId") String executionId,
      @RequestHeader(name = "Last-Event-ID", defaultValue = "0") @Min(0) long after,
      Authentication authentication) {
    return executions.stream(authentication.getName(), executionId, after);
  }

  @PostMapping("/workflow-approvals/{approvalId}/decisions")
  public ApiEnvelope<ExecutionResponse> decide(
      @PathVariable("approvalId") String approvalId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody ApprovalDecisionRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        ExecutionResponse.from(
            executions.decide(
                authentication.getName(),
                approvalId,
                body.decision(),
                body.comment(),
                idempotencyKey)),
        RequestIdFilter.get(request));
  }
}
