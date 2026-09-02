package com.openeip.governance.application.catalog;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.catalog.Model;
import com.openeip.governance.domain.catalog.ModelRegistration;
import com.openeip.governance.domain.catalog.ModelState;
import com.openeip.governance.domain.catalog.Provider;
import com.openeip.governance.domain.catalog.ProviderRegistration;
import com.openeip.governance.domain.catalog.ProviderState;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for versioned provider/model policy and lifecycle changes. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Catalog and audit ports are application-scoped collaborators.")
public class ModelCatalogService {
  private final ModelCatalogPort catalog;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public ModelCatalogService(ModelCatalogPort catalog, AuditService audit) {
    this(catalog, audit, Clock.systemUTC());
  }

  ModelCatalogService(ModelCatalogPort catalog, AuditService audit, Clock clock) {
    this.catalog = catalog;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Provider registerProvider(ProviderRegistration registration) {
    Context context = context(registration.tenantId());
    try {
      Provider provider = catalog.registerProvider(registration);
      audit(
          context, "governance.provider.created", "provider", provider.id(), AuditOutcome.SUCCESS);
      return provider;
    } catch (RuntimeException exception) {
      throw translate(exception);
    }
  }

  @Transactional
  public Provider enableProvider(UUID providerId, long expectedRevision) {
    Context context = context(null);
    Provider provider = provider(context.tenantId(), providerId);
    if (provider.state() != ProviderState.DRAFT && provider.state() != ProviderState.SUSPENDED) {
      throw GovernanceCatalogException.transition(
          "Provider cannot be enabled from its current state");
    }
    if (!catalog.updateProviderState(
        context.tenantId(), providerId, expectedRevision, ProviderState.ENABLED)) {
      throw GovernanceCatalogException.conflict("Provider revision is stale");
    }
    audit(context, "governance.provider.enabled", "provider", providerId, AuditOutcome.SUCCESS);
    return catalog.provider(context.tenantId(), providerId).orElseThrow();
  }

  @Transactional
  public Provider suspendProvider(UUID providerId, long expectedRevision) {
    Context context = context(null);
    Provider provider = provider(context.tenantId(), providerId);
    if (provider.state() != ProviderState.ENABLED) {
      throw GovernanceCatalogException.transition("Only enabled providers can be suspended");
    }
    if (!catalog.updateProviderState(
        context.tenantId(), providerId, expectedRevision, ProviderState.SUSPENDED)) {
      throw GovernanceCatalogException.conflict("Provider revision is stale");
    }
    audit(context, "governance.provider.suspended", "provider", providerId, AuditOutcome.SUCCESS);
    return catalog.provider(context.tenantId(), providerId).orElseThrow();
  }

  @Transactional
  public Model registerModel(ModelRegistration registration) {
    Context context = context(registration.tenantId());
    Provider provider = provider(context.tenantId(), registration.providerId());
    if (provider.state() == ProviderState.DEPRECATED) {
      throw GovernanceCatalogException.transition("Deprecated providers cannot receive models");
    }
    try {
      Model model = catalog.registerModel(registration, context.policyVersion());
      catalog.addVersion(registration, model.id(), 1);
      audit(context, "governance.model.created", "model", model.id(), AuditOutcome.SUCCESS);
      return model;
    } catch (RuntimeException exception) {
      throw translate(exception);
    }
  }

  @Transactional(readOnly = true)
  public List<Model> listModels(String state, String capability, int limit) {
    Context context = context(null);
    if (state != null && !state.isBlank()) {
      try {
        ModelState.valueOf(state.toUpperCase());
      } catch (IllegalArgumentException exception) {
        throw GovernanceCatalogException.invalid("Invalid model state");
      }
    }
    return catalog.models(context.tenantId(), state, capability, Math.min(Math.max(limit, 1), 100));
  }

  @Transactional
  public Model reviewModel(UUID modelId, long expectedRevision) {
    Context context = context(null);
    Model model = model(context.tenantId(), modelId);
    if (model.state() != ModelState.DRAFT) {
      throw GovernanceCatalogException.transition("Only draft models can be reviewed");
    }
    if (catalog.latestVersion(context.tenantId(), modelId).isEmpty()) {
      throw GovernanceCatalogException.transition("A model version is required before review");
    }
    updateModel(
        context, modelId, expectedRevision, ModelState.REVIEWED, "governance.model.reviewed");
    return catalog.model(context.tenantId(), modelId).orElseThrow();
  }

  @Transactional
  public Model enableModel(UUID modelId, long expectedRevision) {
    Context context = context(null);
    Model model = model(context.tenantId(), modelId);
    Provider provider = provider(context.tenantId(), model.providerId());
    if (model.state() != ModelState.REVIEWED) {
      throw GovernanceCatalogException.transition("Only reviewed models can be enabled");
    }
    if (provider.state() != ProviderState.ENABLED) {
      throw GovernanceCatalogException.transition("The model provider must be enabled");
    }
    updateModel(context, modelId, expectedRevision, ModelState.ENABLED, "governance.model.enabled");
    return catalog.model(context.tenantId(), modelId).orElseThrow();
  }

  @Transactional
  public Model suspendModel(UUID modelId, long expectedRevision) {
    Context context = context(null);
    Model model = model(context.tenantId(), modelId);
    if (model.state() != ModelState.ENABLED) {
      throw GovernanceCatalogException.transition("Only enabled models can be suspended");
    }
    updateModel(
        context, modelId, expectedRevision, ModelState.SUSPENDED, "governance.model.suspended");
    return catalog.model(context.tenantId(), modelId).orElseThrow();
  }

  private void updateModel(
      Context context, UUID modelId, long revision, ModelState state, String action) {
    if (!catalog.updateModelState(context.tenantId(), modelId, revision, state.name())) {
      throw GovernanceCatalogException.conflict("Model revision is stale");
    }
    audit(context, action, "model", modelId, AuditOutcome.SUCCESS);
  }

  private void audit(
      Context context, String action, String resourceType, UUID resourceId, AuditOutcome outcome) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            action,
            resourceType,
            resourceId.toString(),
            outcome,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of()));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Catalog command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private Provider provider(UUID tenantId, UUID providerId) {
    return catalog
        .provider(tenantId, providerId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Provider was not found in this tenant"));
  }

  private Model model(UUID tenantId, UUID modelId) {
    return catalog
        .model(tenantId, modelId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Model was not found in this tenant"));
  }

  private static RuntimeException translate(RuntimeException exception) {
    if (exception instanceof GovernanceCatalogException
        || exception instanceof GovernanceAuditException) {
      return exception;
    }
    return GovernanceCatalogException.invalid("Catalog operation is invalid");
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
