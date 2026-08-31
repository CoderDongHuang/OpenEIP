package com.openeip.agent.v2.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateGrantRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.CreateMcpServerRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.ExportMemoryRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.MapMcpToolRequest;
import com.openeip.agent.v2.api.AgentPlatformDtos.PageResponse;
import com.openeip.agent.v2.application.McpGovernanceService;
import com.openeip.agent.v2.application.MemoryGovernanceService;
import com.openeip.agent.v2.application.ToolGovernanceService;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v2")
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring services are application-scoped and intentionally shared.")
public class AgentGovernanceV2Controller {
  private final ToolGovernanceService tools;
  private final MemoryGovernanceService memories;
  private final McpGovernanceService mcp;
  private final AgentPlatformApiSupport api;

  public AgentGovernanceV2Controller(
      ToolGovernanceService tools,
      MemoryGovernanceService memories,
      McpGovernanceService mcp,
      ObjectMapper mapper) {
    this.tools = tools;
    this.memories = memories;
    this.mcp = mcp;
    this.api = new AgentPlatformApiSupport(mapper);
  }

  @GetMapping("/tools")
  public ApiEnvelope<PageResponse> tools(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit, HttpServletRequest request) {
    return success(api.page(tools.tools(limit)), request);
  }

  @GetMapping("/tool-grants")
  public ApiEnvelope<PageResponse> grants(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit, HttpServletRequest request) {
    return success(api.page(tools.grants(limit)), request);
  }

  @PostMapping("/tool-grants")
  public ResponseEntity<ApiEnvelope<JsonNode>> createGrant(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateGrantRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        tools.createGrant(
            authentication.getName(),
            key,
            body.agentVersionId(),
            body.toolVersionId(),
            body.operations(),
            body.resourceSelector(),
            body.argumentConstraints(),
            body.approvalMode(),
            body.expiresAt());
    return response(201, value.revision(), value, request);
  }

  @DeleteMapping("/tool-grants/{grantId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> revokeGrant(
      @PathVariable String grantId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = tools.revoke(authentication.getName(), grantId, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  @GetMapping("/memories")
  public ApiEnvelope<PageResponse> memories(
      @RequestParam(required = false) String purpose,
      @RequestParam(required = false) String state,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return success(
        api.page(memories.list(authentication.getName(), purpose, state, limit)), request);
  }

  @GetMapping("/memories/{memoryId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> memory(
      @PathVariable String memoryId, Authentication authentication, HttpServletRequest request) {
    var value = memories.get(authentication.getName(), memoryId);
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/memories/{memoryId}:quarantine")
  public ResponseEntity<ApiEnvelope<JsonNode>> quarantine(
      @PathVariable String memoryId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = memories.quarantine(authentication.getName(), memoryId, ifMatch, key);
    return response(200, value.revision(), value, request);
  }

  @DeleteMapping("/memories/{memoryId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> deleteMemory(
      @PathVariable String memoryId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = memories.delete(authentication.getName(), memoryId, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  @PostMapping("/memories:export")
  public ResponseEntity<ApiEnvelope<JsonNode>> exportMemories(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ExportMemoryRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    tools.validateIdempotency(authentication.getName(), key);
    return ResponseEntity.accepted()
        .body(
            success(
                api.json(
                    memories.exportMetadata(
                        authentication.getName(), body.purpose(), body.includeContent(), 100)),
                request));
  }

  @GetMapping("/mcp-servers")
  public ApiEnvelope<PageResponse> mcpServers(
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      Authentication authentication,
      HttpServletRequest request) {
    return success(api.page(mcp.list(authentication.getName(), limit)), request);
  }

  @PostMapping("/mcp-servers")
  public ResponseEntity<ApiEnvelope<JsonNode>> createMcpServer(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreateMcpServerRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        mcp.create(
            authentication.getName(),
            key,
            body.name(),
            body.transport(),
            body.endpoint(),
            body.authType(),
            body.credentialRef());
    return response(201, value.revision(), value, request);
  }

  @GetMapping("/mcp-servers/{serverId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> mcpServer(
      @PathVariable String serverId, Authentication authentication, HttpServletRequest request) {
    var value = mcp.get(authentication.getName(), serverId);
    return response(200, value.revision(), value, request);
  }

  @PatchMapping(value = "/mcp-servers/{serverId}", consumes = "application/merge-patch+json")
  public ResponseEntity<ApiEnvelope<JsonNode>> updateMcpServer(
      @PathVariable String serverId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestBody JsonNode body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        mcp.update(
            authentication.getName(),
            serverId,
            ifMatch,
            text(body, "name"),
            text(body, "endpoint"));
    return response(200, value.revision(), value, request);
  }

  @DeleteMapping("/mcp-servers/{serverId}")
  public ResponseEntity<ApiEnvelope<JsonNode>> disableMcpServer(
      @PathVariable String serverId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    var value = mcp.disable(authentication.getName(), serverId, ifMatch, key);
    return response(202, value.revision(), value, request);
  }

  @PostMapping("/mcp-servers/{serverId}:test")
  public ResponseEntity<ApiEnvelope<JsonNode>> testMcpServer(
      @PathVariable String serverId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    return ResponseEntity.accepted()
        .body(
            success(api.json(mcp.test(authentication.getName(), serverId, ifMatch, key)), request));
  }

  @PostMapping("/mcp-servers/{serverId}:discover")
  public ResponseEntity<ApiEnvelope<JsonNode>> discoverMcpServer(
      @PathVariable String serverId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      Authentication authentication,
      HttpServletRequest request) {
    return ResponseEntity.accepted()
        .body(
            success(
                api.json(mcp.discover(authentication.getName(), serverId, ifMatch, key)), request));
  }

  @PostMapping("/mcp-servers/{serverId}/capabilities/{capabilityId}:map-tool")
  public ResponseEntity<ApiEnvelope<JsonNode>> mapMcpTool(
      @PathVariable String serverId,
      @PathVariable String capabilityId,
      @RequestHeader("If-Match") String ifMatch,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody MapMcpToolRequest body,
      Authentication authentication,
      HttpServletRequest request) {
    var value =
        mcp.mapTool(
            authentication.getName(), serverId, capabilityId, body.toolVersionId(), ifMatch, key);
    return ResponseEntity.status(201).body(success(api.json(value), request));
  }

  private static String text(JsonNode body, String field) {
    return body.has(field) ? body.path(field).asText(null) : null;
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
