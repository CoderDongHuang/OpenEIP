package com.openeip.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.workflow.api.WorkflowDtos.ExecutionResponse;
import com.openeip.workflow.application.WorkflowTriggerService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-hooks")
public class WorkflowWebhookController {
  private final WorkflowTriggerService triggers;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Spring collaborator is shared.")
  public WorkflowWebhookController(WorkflowTriggerService triggers) {
    this.triggers = triggers;
  }

  @PostMapping("/{hookId}")
  public ResponseEntity<ApiEnvelope<ExecutionResponse>> invoke(
      @PathVariable("hookId") String hookId,
      @RequestHeader("X-Workflow-Hook-Secret") String secret,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestBody JsonNode input,
      HttpServletRequest request) {
    ExecutionResponse data =
        ExecutionResponse.from(triggers.invoke(hookId, secret, idempotencyKey, input));
    return ResponseEntity.accepted().body(ApiEnvelope.success(data, RequestIdFilter.get(request)));
  }
}
