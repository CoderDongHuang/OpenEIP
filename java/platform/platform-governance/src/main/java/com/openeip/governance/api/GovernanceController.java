package com.openeip.governance.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.governance.application.GovernanceReadService;
import com.openeip.governance.application.budget.BudgetService;
import com.openeip.governance.application.catalog.ModelCatalogService;
import com.openeip.governance.application.catalog.PromptCatalogService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.usage.UsageLedgerService;
import com.openeip.governance.domain.budget.BudgetRegistration;
import com.openeip.governance.domain.budget.BudgetWindowType;
import com.openeip.governance.domain.catalog.PromptRegistration;
import com.openeip.governance.domain.catalog.PromptVersionRegistration;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-scoped Governance v2 management API. */
@Validated
@RestController
@RequestMapping("/api/v2/governance")
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Injected Spring services and providers are application-scoped collaborators.")
public class GovernanceController {
  private final GovernanceReadService reads;
  private final ModelCatalogService models;
  private final ObjectProvider<PromptCatalogService> prompts;
  private final UsageLedgerService usage;
  private final BudgetService budgets;

  public GovernanceController(
      GovernanceReadService reads,
      ModelCatalogService models,
      ObjectProvider<PromptCatalogService> prompts,
      UsageLedgerService usage,
      BudgetService budgets) {
    this.reads = reads;
    this.models = models;
    this.prompts = prompts;
    this.usage = usage;
    this.budgets = budgets;
  }

  @GetMapping("/tenants")
  public ApiEnvelope<GovernanceDtos.CursorPage> tenants(HttpServletRequest request) {
    return success(new GovernanceDtos.CursorPage(List.of(reads.currentTenant()), null), request);
  }

  @GetMapping("/tenants/{tenantId}")
  public ResponseEntity<ApiEnvelope<Object>> tenant(
      @PathVariable UUID tenantId, HttpServletRequest request) {
    requireCurrentTenant(tenantId);
    var value = reads.currentTenant();
    return response(200, ((Number) value.get("revision")).longValue(), value, request);
  }

  @GetMapping("/tenants/{tenantId}/memberships")
  public ApiEnvelope<GovernanceDtos.CursorPage> memberships(
      @PathVariable UUID tenantId,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    requireCurrentTenant(tenantId);
    return success(new GovernanceDtos.CursorPage(reads.memberships(limit), null), request);
  }

  @GetMapping("/audit-events")
  public ApiEnvelope<GovernanceDtos.CursorPage> audits(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String outcome,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(
        new GovernanceDtos.CursorPage(
            reads.audits(action, resourceType, outcome, from, to, limit), null),
        request);
  }

  @PostMapping("/audit-events:verify")
  public ApiEnvelope<Object> verifyAudit(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.VerifyAuditRequest body,
      HttpServletRequest request) {
    requireOperator();
    requireKey(key);
    return success(reads.verifyAudit(body.from(), body.to()), request);
  }

  @GetMapping("/models")
  public ApiEnvelope<GovernanceDtos.CursorPage> models(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String capability,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(
        new GovernanceDtos.CursorPage(models.listModels(state, capability, limit), null), request);
  }

  @PostMapping("/models")
  public ResponseEntity<ApiEnvelope<Object>> createModel(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.CreateModelRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var value =
        models.registerModelPolicy(
            body.name(),
            body.providerRef(),
            body.secretRef(),
            Set.copyOf(body.capabilities()),
            body.routingLabels() == null ? Set.of() : Set.copyOf(body.routingLabels()));
    return response(201, value.revision(), value, request);
  }

  @PostMapping("/models/{modelId}:review")
  public ResponseEntity<ApiEnvelope<Object>> reviewModel(
      @PathVariable UUID modelId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      @Valid @RequestBody GovernanceDtos.ReviewRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    if (!"APPROVE".equalsIgnoreCase(body.decision())) {
      throw GovernanceCatalogException.transition("Rejected models remain draft");
    }
    var value = models.reviewModel(modelId, revision(revision));
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/models/{modelId}:enable")
  public ResponseEntity<ApiEnvelope<Object>> enableModel(
      @PathVariable UUID modelId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var value = models.enableModel(modelId, revision(revision));
    return response(200, value.revision(), value, request);
  }

  @PostMapping("/models/{modelId}:suspend")
  public ResponseEntity<ApiEnvelope<Object>> suspendModel(
      @PathVariable UUID modelId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var value = models.suspendModel(modelId, revision(revision));
    return response(200, value.revision(), value, request);
  }

  @GetMapping("/prompts")
  public ApiEnvelope<GovernanceDtos.CursorPage> prompts(
      @RequestParam(required = false) String lifecycle,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(
        new GovernanceDtos.CursorPage(promptService().listPrompts(lifecycle, limit), null),
        request);
  }

  @PostMapping("/prompts")
  public ResponseEntity<ApiEnvelope<Object>> createPrompt(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.CreatePromptRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var context = TenantContextHolder.required();
    var value =
        promptService()
            .createPrompt(
                new PromptRegistration(
                    context.tenantId(),
                    body.name(),
                    body.purpose(),
                    body.content(),
                    "v1",
                    context.principalId(),
                    Instant.now()));
    return response(201, value.revision(), value, request);
  }

  @PostMapping("/prompts/{promptId}/versions")
  public ResponseEntity<ApiEnvelope<Object>> createPromptVersion(
      @PathVariable UUID promptId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.CreatePromptVersionRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var context = TenantContextHolder.required();
    var value =
        promptService()
            .createVersion(
                new PromptVersionRegistration(
                    context.tenantId(),
                    promptId,
                    body.content(),
                    body.compatibilityVersion(),
                    context.principalId(),
                    Instant.now()));
    return response(201, 0, value, request);
  }

  @PostMapping("/prompts/{promptId}/versions/{versionId}:review")
  public ApiEnvelope<Object> reviewPrompt(
      @PathVariable UUID promptId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.ReviewRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    return success(
        promptService().reviewVersion(promptId, versionId, body.decision(), body.reason()),
        request);
  }

  @PostMapping("/prompts/{promptId}/versions/{versionId}:evaluate")
  public ResponseEntity<ApiEnvelope<Object>> evaluatePrompt(
      @PathVariable UUID promptId,
      @PathVariable UUID versionId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.EvaluateRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    return ResponseEntity.accepted()
        .body(
            success(promptService().evaluateVersion(promptId, versionId, body.suiteId()), request));
  }

  @PostMapping("/prompts/{promptId}:publish")
  public ResponseEntity<ApiEnvelope<Object>> publishPrompt(
      @PathVariable UUID promptId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("If-Match") String revision,
      @Valid @RequestBody GovernanceDtos.PublishPromptRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var value =
        promptService()
            .publish(
                promptId,
                required(body.versionId(), "versionId"),
                required(body.evaluationRunId(), "evaluationRunId").toString(),
                revision(revision));
    return response(201, value.revision(), value, request);
  }

  @PostMapping("/prompts/{promptId}:rollback")
  public ResponseEntity<ApiEnvelope<Object>> rollbackPrompt(
      @PathVariable UUID promptId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.RollbackPromptRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var value =
        promptService().rollback(promptId, required(body.versionId(), "versionId"), body.reason());
    return response(201, value.revision(), value, request);
  }

  @GetMapping("/usage")
  public ApiEnvelope<GovernanceDtos.CursorPage> usage(
      @RequestParam(required = false) UUID executionId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
      HttpServletRequest request) {
    return success(
        new GovernanceDtos.CursorPage(usage.list(executionId, from, to, limit), null), request);
  }

  @GetMapping("/budgets")
  public ApiEnvelope<GovernanceDtos.CursorPage> budgets(
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit, HttpServletRequest request) {
    return success(new GovernanceDtos.CursorPage(budgets.list(limit), null), request);
  }

  @PostMapping("/budgets")
  public ResponseEntity<ApiEnvelope<Object>> createBudget(
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody GovernanceDtos.CreateBudgetRequest body,
      HttpServletRequest request) {
    requireAdmin();
    requireKey(key);
    var context = TenantContextHolder.required();
    BudgetWindowType window;
    try {
      window = BudgetWindowType.valueOf(body.window().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid("Budget window is invalid");
    }
    var value =
        budgets.create(
            new BudgetRegistration(
                context.tenantId(),
                body.name(),
                body.currency(),
                body.limit(),
                window,
                Instant.now()));
    return response(201, value.revision(), value, request);
  }

  @GetMapping("/traces/{traceId}")
  public ApiEnvelope<Object> trace(
      @PathVariable @Pattern(regexp = "[a-f0-9]{16,32}") String traceId,
      HttpServletRequest request) {
    return success(new GovernanceDtos.CursorPage(reads.traces(traceId, 100), null), request);
  }

  private PromptCatalogService promptService() {
    PromptCatalogService service = prompts.getIfAvailable();
    if (service == null) {
      throw GovernanceCatalogException.invalid("Prompt encryption is not configured");
    }
    return service;
  }

  private static void requireCurrentTenant(UUID tenantId) {
    if (!TenantContextHolder.required().tenantId().equals(tenantId)) {
      throw new GovernanceAuthorizationException("Cross-tenant access is denied");
    }
  }

  private static void requireOperator() {
    var roles = TenantContextHolder.required().roles();
    if (!roles.contains("OPERATOR") && !roles.contains("GOVERNANCE_ADMIN")) {
      throw new GovernanceAuthorizationException("Governance operator role is required");
    }
  }

  private static void requireAdmin() {
    if (!TenantContextHolder.required().roles().contains("GOVERNANCE_ADMIN")) {
      throw new GovernanceAuthorizationException("Governance administrator role is required");
    }
  }

  private static void requireKey(String key) {
    if (key == null || key.length() < 16 || key.length() > 128 || key.isBlank()) {
      throw GovernanceCatalogException.invalid("Idempotency-Key is invalid");
    }
  }

  private static long revision(String value) {
    try {
      String normalized = value == null ? "" : value.replace("\"", "");
      long revision = Long.parseLong(normalized);
      if (revision < 0) {
        throw new NumberFormatException("negative");
      }
      return revision;
    } catch (NumberFormatException exception) {
      throw GovernanceCatalogException.invalid("If-Match revision is invalid");
    }
  }

  private static UUID required(UUID value, String field) {
    if (value == null) {
      throw GovernanceCatalogException.invalid(field + " is required");
    }
    return value;
  }

  private static <T> ApiEnvelope<T> success(T value, HttpServletRequest request) {
    return ApiEnvelope.success(value, RequestIdFilter.get(request));
  }

  private static ResponseEntity<ApiEnvelope<Object>> response(
      int status, long revision, Object value, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .eTag(Long.toString(revision))
        .body(success(value, request));
  }
}
