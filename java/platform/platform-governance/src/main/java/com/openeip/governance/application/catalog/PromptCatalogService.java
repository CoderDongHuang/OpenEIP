package com.openeip.governance.application.catalog;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.catalog.Prompt;
import com.openeip.governance.domain.catalog.PromptRegistration;
import com.openeip.governance.domain.catalog.PromptReview;
import com.openeip.governance.domain.catalog.PromptState;
import com.openeip.governance.domain.catalog.PromptVersion;
import com.openeip.governance.domain.catalog.PromptVersionRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for encrypted, reviewed, evaluated, and published Prompt versions. */
@Service
@ConditionalOnBean(PromptContentCipher.class)
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Catalog, audit, and cipher ports are application-scoped collaborators.")
public class PromptCatalogService {
  private final PromptCatalogPort catalog;
  private final AuditService audit;
  private final PromptContentCipher cipher;
  private final Clock clock;

  @Autowired
  public PromptCatalogService(
      PromptCatalogPort catalog, AuditService audit, PromptContentCipher cipher) {
    this(catalog, audit, cipher, Clock.systemUTC());
  }

  PromptCatalogService(
      PromptCatalogPort catalog, AuditService audit, PromptContentCipher cipher, Clock clock) {
    this.catalog = catalog;
    this.audit = audit;
    this.cipher = cipher;
    this.clock = clock;
  }

  @Transactional
  public Prompt createPrompt(PromptRegistration registration) {
    Context context = context(registration.tenantId());
    try {
      String digest = cipher.digest(registration.content());
      Prompt prompt =
          catalog.createPrompt(registration, cipher.encrypt(registration.content()), digest);
      audit(context, "governance.prompt.created", prompt.id());
      return prompt;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Prompt creation is invalid");
    }
  }

  @Transactional
  public PromptVersion createVersion(PromptVersionRegistration registration) {
    Context context = context(registration.tenantId());
    prompt(context.tenantId(), registration.promptId());
    try {
      String digest = cipher.digest(registration.content());
      int versionNumber =
          catalog
                  .latestVersion(context.tenantId(), registration.promptId())
                  .map(PromptVersion::versionNumber)
                  .orElse(0)
              + 1;
      PromptVersion version =
          catalog.createVersion(
              registration, cipher.encrypt(registration.content()), digest, versionNumber);
      audit(context, "governance.prompt.version.created", version.id());
      return version;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Prompt version creation is invalid");
    }
  }

  @Transactional(readOnly = true)
  public List<Prompt> listPrompts(String state, int limit) {
    Context context = context(null);
    if (state != null && !state.isBlank()) {
      try {
        PromptState.valueOf(state.toUpperCase());
      } catch (IllegalArgumentException exception) {
        throw GovernanceCatalogException.invalid("Invalid Prompt state");
      }
    }
    return catalog.prompts(context.tenantId(), state, Math.min(Math.max(limit, 1), 100));
  }

  @Transactional
  public PromptReview reviewVersion(UUID promptId, UUID versionId, String decision, String reason) {
    Context context = context(null);
    PromptVersion version = version(context.tenantId(), promptId, versionId);
    String normalizedDecision = decision == null ? "" : decision.toUpperCase();
    if (!normalizedDecision.equals("APPROVE") && !normalizedDecision.equals("REJECT")) {
      throw GovernanceCatalogException.invalid("Review decision is invalid");
    }
    PromptReview review =
        catalog.addReview(
            context.tenantId(),
            version.id(),
            context.principalId(),
            normalizedDecision,
            bounded(reason, "reason", 512),
            context.policyVersion(),
            null,
            clock.instant());
    audit(context, "governance.prompt.version.reviewed", version.id());
    return review;
  }

  @Transactional
  public PromptReview evaluateVersion(UUID promptId, UUID versionId, UUID suiteId) {
    Context context = context(null);
    PromptVersion version = version(context.tenantId(), promptId, versionId);
    if (!catalog.hasApprovedReview(context.tenantId(), version.id())) {
      throw GovernanceCatalogException.transition(
          "Prompt version requires approval before evaluation");
    }
    PromptReview evaluation =
        catalog.addReview(
            context.tenantId(),
            version.id(),
            context.principalId(),
            "APPROVE",
            "Evaluation suite "
                + bounded(suiteId == null ? null : suiteId.toString(), "suiteId", 64),
            context.policyVersion(),
            UUID.randomUUID(),
            clock.instant());
    audit(context, "governance.prompt.version.evaluated", version.id());
    return evaluation;
  }

  @Transactional
  public Prompt publish(
      UUID promptId, UUID versionId, String evaluationRunId, long expectedRevision) {
    Context context = context(null);
    Prompt prompt = prompt(context.tenantId(), promptId);
    PromptVersion version = version(context.tenantId(), promptId, versionId);
    if (prompt.revision() != expectedRevision) {
      throw GovernanceCatalogException.conflict("Prompt revision is stale");
    }
    UUID evaluationId = parseUuid(evaluationRunId, "evaluationRunId");
    if (!catalog.hasApprovedReview(context.tenantId(), version.id())
        || !catalog.hasEvaluation(context.tenantId(), version.id(), evaluationId)) {
      throw GovernanceCatalogException.transition(
          "Prompt version requires matching review and evaluation");
    }
    catalog.publish(
        context.tenantId(),
        promptId,
        version.id(),
        version.contentDigest(),
        "published",
        context.policyVersion(),
        context.principalId(),
        clock.instant(),
        expectedRevision);
    audit(context, "governance.prompt.published", version.id());
    return catalog.prompt(context.tenantId(), promptId).orElseThrow();
  }

  @Transactional
  public Prompt rollback(UUID promptId, UUID versionId, String reason) {
    Context context = context(null);
    Prompt prompt = prompt(context.tenantId(), promptId);
    PromptVersion version = version(context.tenantId(), promptId, versionId);
    if (!catalog.hasApprovedReview(context.tenantId(), version.id())
        || !catalog.hasEvaluation(context.tenantId(), version.id(), null)) {
      throw GovernanceCatalogException.transition("Rollback target requires review and evaluation");
    }
    catalog.publish(
        context.tenantId(),
        promptId,
        version.id(),
        version.contentDigest(),
        bounded(reason, "reason", 512),
        context.policyVersion(),
        context.principalId(),
        clock.instant(),
        prompt.revision());
    audit(context, "governance.prompt.rolled_back", version.id());
    return catalog.prompt(context.tenantId(), prompt.id()).orElseThrow();
  }

  private Prompt prompt(UUID tenantId, UUID promptId) {
    return catalog
        .prompt(tenantId, promptId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Prompt was not found in this tenant"));
  }

  private PromptVersion version(UUID tenantId, UUID promptId, UUID versionId) {
    return catalog
        .version(tenantId, promptId, versionId)
        .orElseThrow(
            () ->
                GovernanceCatalogException.invalid("Prompt version was not found in this tenant"));
  }

  private void audit(Context context, String action, UUID resourceId) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            action,
            "prompt",
            resourceId.toString(),
            AuditOutcome.SUCCESS,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of("versionId", resourceId.toString())));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Prompt command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw GovernanceCatalogException.invalid(field + " is required and bounded");
    }
    return value;
  }

  private static UUID parseUuid(String value, String field) {
    try {
      return UUID.fromString(bounded(value, field, 128));
    } catch (IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid(field + " must be a UUID");
    }
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
