package com.openeip.governance.application.usage;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.catalog.ModelCatalogPort;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.catalog.Model;
import com.openeip.governance.domain.usage.PricingSnapshot;
import com.openeip.governance.domain.usage.PricingSnapshotRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for immutable tenant-scoped pricing snapshots. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Catalog and audit ports are application-scoped collaborators.")
public class PricingSnapshotService {
  private final PricingSnapshotPort pricing;
  private final ModelCatalogPort models;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public PricingSnapshotService(
      PricingSnapshotPort pricing, ModelCatalogPort models, AuditService audit) {
    this(pricing, models, audit, Clock.systemUTC());
  }

  PricingSnapshotService(
      PricingSnapshotPort pricing, ModelCatalogPort models, AuditService audit, Clock clock) {
    this.pricing = pricing;
    this.models = models;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public PricingSnapshot create(PricingSnapshotRegistration registration) {
    Context context = context(registration.tenantId());
    Model model =
        models
            .model(context.tenantId(), registration.modelId())
            .orElseThrow(
                () -> GovernanceCatalogException.invalid("Model was not found in this tenant"));
    if (!model.providerId().equals(registration.providerId())) {
      throw GovernanceCatalogException.invalid("Pricing provider does not own the model");
    }
    try {
      PricingSnapshot snapshot = pricing.create(registration);
      audit(context, "governance.pricing.created", snapshot.id());
      return snapshot;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Pricing snapshot is invalid");
    }
  }

  private void audit(Context context, String action, UUID resourceId) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            action,
            "pricing",
            resourceId.toString(),
            AuditOutcome.SUCCESS,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of()));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant()) || !expectedTenantId.equals(value.tenantId())) {
      throw GovernanceCatalogException.invalid("Pricing command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
