package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateAgentRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.PageResponse;
import com.openeip.agent.v2.api.AgentPlatformDtos.PublishAgentRequest;
import com.openeip.agent.v2.application.AgentDefinitionService;
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
@RequestMapping("/api/v2/agents")
public class AgentDefinitionV2Controller {
  private final AgentDefinitionService definitions;
  private final AgentPlatformApiSupport api;

  public AgentDefinitionV2Controller(AgentDefinitionService definitions, ObjectMapper mapper) {
    this.definitions = definitions;
    this.api = new AgentPlatformApiSupport(mapper);
  }

  @GetMapping
  public ApiEnvelope<PageResponse> list(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return success(api.page(definitions.list(authentication.getName(), limit)), request);
  }

  @PostMapping
  public ResponseEntity<ApiEnvelope<JsonNode>> create(
      @Valid @RequestBody CreateAgentRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        definitions.create(authentication.getName(), body.name(), body.type(), body.description());
    return response(201, value.revision(), api.json(value), request);
  }

  @GetMapping("/{agentId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> get(
      @PathVariable String agentId, Authentication authentication, HttpServletRequest request) {
    var value = definitions.get(authentication.getName(), agentId);
    return response(200, value.revision(), api.json(value), request);
  }

  @PatchMapping(value = "/{agentId}", consumes = "application/merge-patch+json")
  public ResponseEntity<ApiEnvelope<JsonNode>> update(
      @PathVariable String agentId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody JsonNode body,
      Authentication authentication,
      HttpServletRequest request) {
    var value = definitions.update(authentication.getName(), agentId, ifMatch, body);
    return response(200, value.revision(), api.json(value), request);
  }

  @GetMapping("/{agentId}/versions")
  public ApiEnvelope<PageResponse> versions(
      @PathVariable String agentId, Authentication authentication, HttpServletRequest request) {
    return success(api.page(definitions.versions(authentication.getName(), agentId)), request);
  }

  @PostMapping("/{agentId}:archive")
  public ResponseEntity<ApiEnvelope<JsonNode>> archive(
      @PathVariable String agentId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    var value = definitions.archive(authentication.getName(), agentId, ifMatch, idempotencyKey);
    return response(200, value.revision(), api.json(value), request);
  }

  @PostMapping("/{agentId}/versions:candidate")
  public ResponseEntity<ApiEnvelope<JsonNode>> candidate(
      @PathVariable String agentId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        definitions.createCandidate(authentication.getName(), agentId, ifMatch, idempotencyKey);
    return ResponseEntity.status(201)
        .body(ApiEnvelope.success(api.json(value), RequestIdFilter.get(request)));
  }

  @PostMapping("/{agentId}/versions/{version}:restore-draft")
  public ResponseEntity<ApiEnvelope<JsonNode>> restore(
      @PathVariable String agentId,
      @PathVariable @Min(1) int version,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        definitions.restore(authentication.getName(), agentId, version, ifMatch, idempotencyKey);
    return response(201, value.revision(), api.json(value), request);
  }

  @PostMapping("/{agentId}/versions:publish")
  public ResponseEntity<ApiEnvelope<JsonNode>> publish(
      @PathVariable String agentId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody PublishAgentRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        definitions.publish(
            authentication.getName(), agentId, ifMatch, idempotencyKey, body.evaluationRunId());
    return ResponseEntity.status(201)
        .body(ApiEnvelope.success(api.json(value), RequestIdFilter.get(request)));
  }

  private static <T> ApiEnvelope<T> success(T value, HttpServletRequest request) {
    return ApiEnvelope.success(value, RequestIdFilter.get(request));
  }

  private static ResponseEntity<ApiEnvelope<JsonNode>> response(
      int status, long revision, JsonNode value, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .eTag(Long.toString(revision))
        .body(ApiEnvelope.success(value, RequestIdFilter.get(request)));
  }
}
