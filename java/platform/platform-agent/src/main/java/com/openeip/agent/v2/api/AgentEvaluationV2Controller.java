package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateDatasetRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateEvaluationRunRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateSuiteRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.PageResponse;
import com.openeip.agent.v2.application.AgentEvaluationService;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class AgentEvaluationV2Controller {
  private final AgentEvaluationService evaluations;
  private final AgentPlatformApiSupport api;

  public AgentEvaluationV2Controller(AgentEvaluationService evaluations, ObjectMapper mapper) {
    this.evaluations = evaluations;
    this.api = new AgentPlatformApiSupport(mapper);
  }

  @GetMapping("/evaluation-datasets")
  public ApiEnvelope<PageResponse> datasets(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return success(api.page(evaluations.datasets(authentication.getName(), limit)), request);
  }

  @PostMapping("/evaluation-datasets")
  public ResponseEntity<ApiEnvelope<JsonNode>> createDataset(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateDatasetRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        evaluations.createDataset(authentication.getName(), key, body.name(), body.description());
    return response(201, value.revision(), value, request);
  }

  @GetMapping("/evaluation-suites")
  public ApiEnvelope<PageResponse> suites(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return success(api.page(evaluations.suites(authentication.getName(), limit)), request);
  }

  @PostMapping("/evaluation-suites")
  public ResponseEntity<ApiEnvelope<JsonNode>> createSuite(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateSuiteRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        evaluations.createSuite(
            authentication.getName(),
            key,
            body.name(),
            body.datasetVersionIds(),
            body.gatePolicy());
    return response(201, value.revision(), value, request);
  }

  @PostMapping("/evaluation-runs")
  public ResponseEntity<ApiEnvelope<JsonNode>> run(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateEvaluationRunRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    int repeats = body.repeatCount() == null ? 1 : body.repeatCount();
    var value =
        evaluations.run(
            authentication.getName(),
            key,
            body.suiteVersionId(),
            body.candidateAgentVersionId(),
            body.baselineAgentVersionId(),
            repeats);
    return response(202, value.revision(), value, request);
  }

  @GetMapping("/evaluation-runs/{evaluationRunId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> getRun(
      @PathVariable String evaluationRunId,
      Authentication authentication,
      HttpServletRequest request) {
    var value = evaluations.get(authentication.getName(), evaluationRunId);
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/evaluation-runs/{evaluationRunId}:cancel")
  public ResponseEntity<ApiEnvelope<JsonNode>> cancelRun(
      @PathVariable String evaluationRunId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = evaluations.cancel(authentication.getName(), evaluationRunId, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  private static <T> ApiEnvelope<T> success(T value, HttpServletRequest request) {
    return ApiEnvelope.success(value, RequestIdFilter.get(request));
  }

  private ResponseEntity<ApiEnvelope<JsonNode>> response(
      int status, long revision, Object value, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .eTag(Long.toString(revision))
        .body(success(api.json(value), request));
  }
}
