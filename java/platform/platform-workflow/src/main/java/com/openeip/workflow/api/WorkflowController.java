package com.openeip.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.workflow.api.WorkflowDtos.CreateTriggerRequest;
import com.openeip.workflow.api.WorkflowDtos.CreateWorkflowRequest;
import com.openeip.workflow.api.WorkflowDtos.ExecutionResponse;
import com.openeip.workflow.api.WorkflowDtos.PageResponse;
import com.openeip.workflow.api.WorkflowDtos.TriggerExecutionRequest;
import com.openeip.workflow.api.WorkflowDtos.TriggerResponse;
import com.openeip.workflow.api.WorkflowDtos.UpdateWorkflowRequest;
import com.openeip.workflow.api.WorkflowDtos.VersionResponse;
import com.openeip.workflow.api.WorkflowDtos.WorkflowResponse;
import com.openeip.workflow.application.WorkflowExecutionService;
import com.openeip.workflow.application.WorkflowGraphValidator;
import com.openeip.workflow.application.WorkflowService;
import com.openeip.workflow.application.WorkflowTriggerService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
  private final WorkflowService workflows;
  private final WorkflowExecutionService executions;
  private final WorkflowTriggerService triggers;
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring collaborators are shared services.")
  public WorkflowController(
      WorkflowService workflows,
      WorkflowExecutionService executions,
      WorkflowTriggerService triggers,
      ObjectMapper mapper) {
    this.workflows = workflows;
    this.executions = executions;
    this.triggers = triggers;
    this.mapper = mapper;
  }

  @PostMapping
  public ResponseEntity<ApiEnvelope<WorkflowResponse>> create(
      @Valid @RequestBody CreateWorkflowRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    WorkflowResponse data =
        WorkflowResponse.from(
            workflows.create(authentication.getName(), body.name(), body.description()), mapper);
    return ResponseEntity.status(201).body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping
  public ApiEnvelope<PageResponse<WorkflowResponse>> list(
      @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        workflows
            .list(authentication.getName(), page, size)
            .map(value -> WorkflowResponse.from(value, mapper));
    return ApiEnvelope.success(PageResponse.from(result), RequestIdFilter.get(request));
  }

  @GetMapping("/{workflowId}")
  public ApiEnvelope<WorkflowResponse> get(
      @PathVariable("workflowId") String workflowId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        WorkflowResponse.from(workflows.get(authentication.getName(), workflowId), mapper),
        RequestIdFilter.get(request));
  }

  @PatchMapping("/{workflowId}")
  public ApiEnvelope<WorkflowResponse> update(
      @PathVariable("workflowId") String workflowId,
      @RequestHeader("If-Match") long revision,
      @Valid @RequestBody UpdateWorkflowRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var data =
        workflows.update(
            authentication.getName(),
            workflowId,
            revision,
            body.name(),
            body.description(),
            body.graph());
    return ApiEnvelope.success(WorkflowResponse.from(data, mapper), RequestIdFilter.get(request));
  }

  @DeleteMapping("/{workflowId}")
  public ResponseEntity<Void> delete(
      @PathVariable("workflowId") String workflowId, Authentication authentication) {
    workflows.delete(authentication.getName(), workflowId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{workflowId}/validate")
  public ApiEnvelope<WorkflowGraphValidator.ValidationResult> validate(
      @PathVariable("workflowId") String workflowId,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        workflows.validate(authentication.getName(), workflowId), RequestIdFilter.get(request));
  }

  @PostMapping("/{workflowId}/publish")
  public ResponseEntity<ApiEnvelope<VersionResponse>> publish(
      @PathVariable("workflowId") String workflowId,
      @RequestHeader("If-Match") long revision,
      Authentication authentication,
      HttpServletRequest request) {
    VersionResponse data =
        VersionResponse.from(
            workflows.publish(authentication.getName(), workflowId, revision), mapper);
    return ResponseEntity.status(201).body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping("/{workflowId}/versions")
  public ApiEnvelope<List<VersionResponse>> versions(
      @PathVariable("workflowId") String workflowId,
      Authentication authentication,
      HttpServletRequest request) {
    List<VersionResponse> data =
        workflows.versions(authentication.getName(), workflowId).stream()
            .map(value -> VersionResponse.from(value, mapper))
            .toList();
    return ApiEnvelope.success(data, RequestIdFilter.get(request));
  }

  @PostMapping("/{workflowId}/versions/{version}/restore")
  public ApiEnvelope<WorkflowResponse> restore(
      @PathVariable("workflowId") String workflowId,
      @PathVariable("version") @Min(1) int version,
      Authentication authentication,
      HttpServletRequest request) {
    return ApiEnvelope.success(
        WorkflowResponse.from(
            workflows.restore(authentication.getName(), workflowId, version), mapper),
        RequestIdFilter.get(request));
  }

  @GetMapping("/{workflowId}/executions")
  public ApiEnvelope<PageResponse<ExecutionResponse>> executions(
      @PathVariable("workflowId") String workflowId,
      @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      Authentication authentication,
      HttpServletRequest request) {
    var result =
        executions
            .list(authentication.getName(), workflowId, page, size)
            .map(ExecutionResponse::from);
    return ApiEnvelope.success(PageResponse.from(result), RequestIdFilter.get(request));
  }

  @PostMapping("/{workflowId}/executions")
  public ResponseEntity<ApiEnvelope<ExecutionResponse>> execute(
      @PathVariable("workflowId") String workflowId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TriggerExecutionRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    ExecutionResponse data =
        ExecutionResponse.from(
            executions.trigger(authentication.getName(), workflowId, idempotencyKey, body.input()));
    return ResponseEntity.accepted().body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }

  @GetMapping("/{workflowId}/triggers")
  public ApiEnvelope<List<TriggerResponse>> triggers(
      @PathVariable("workflowId") String workflowId,
      Authentication authentication,
      HttpServletRequest request) {
    List<TriggerResponse> data =
        triggers.list(authentication.getName(), workflowId).stream()
            .map(value -> TriggerResponse.from(value, null, mapper))
            .toList();
    return ApiEnvelope.success(data, RequestIdFilter.get(request));
  }

  @PostMapping("/{workflowId}/triggers")
  public ResponseEntity<ApiEnvelope<TriggerResponse>> createTrigger(
      @PathVariable("workflowId") String workflowId,
      @Valid @RequestBody CreateTriggerRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var created =
        triggers.create(
            authentication.getName(), workflowId, body.type(), body.enabled(), body.config());
    TriggerResponse data = TriggerResponse.from(created.trigger(), created.secret(), mapper);
    return ResponseEntity.status(201).body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }
}
