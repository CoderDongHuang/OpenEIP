package com.openeip.governance.application.quota;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.quota.QuotaPolicy;
import com.openeip.governance.domain.quota.QuotaPolicyRegistration;
import com.openeip.governance.domain.quota.QuotaPolicyUpdate;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for tenant-scoped quota policy CRUD with optimistic concurrency. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Quota and audit ports are application-scoped collaborators.")
public class QuotaPolicyService {
  private final QuotaPolicyPort quotas;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public QuotaPolicyService(QuotaPolicyPort quotas, AuditService audit) {
    this(quotas, audit, Clock.systemUTC());
  }

  QuotaPolicyService(QuotaPolicyPort quotas, AuditService audit, Clock clock) {
    this.quotas = quotas;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public QuotaPolicy create(QuotaPolicyRegistration registration) {
    Context context = context(registration.tenantId());
    try {
      QuotaPolicy quota = quotas.create(registration, context.policyVersion());
      audit(context, "governance.quota.created", quota.id(), AuditOutcome.SUCCESS);
      return quota;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Quota policy creation is invalid");
    }
  }

  @Transactional(readOnly = true)
  public QuotaPolicy get(UUID quotaPolicyId) {
    Context context = context(null);
    return quotas
        .quota(context.tenantId(), quotaPolicyId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Quota policy was not found in this tenant"));
  }

  @Transactional(readOnly = true)
  public List<QuotaPolicy> list(int limit) {
    Context context = context(null);
    return quotas.quotas(context.tenantId(), Math.min(Math.max(limit, 1), 100));
  }

  @Transactional
  public QuotaPolicy update(QuotaPolicyUpdate update) {
    Context context = context(update.tenantId());
    if (!quotas.update(update, context.policyVersion())) {
      throw GovernanceCatalogException.conflict("Quota policy revision is stale");
    }
    QuotaPolicy quota = get(update.quotaPolicyId());
    audit(context, "governance.quota.updated", quota.id(), AuditOutcome.SUCCESS);
    return quota;
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    Instant now = clock.instant();
    if (value.expiredAt(now)
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Quota command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
  }

  private void audit(Context context, String action, UUID quotaId, AuditOutcome outcome) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            action,
            "quota-policy",
            quotaId.toString(),
            outcome,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of()));
  }

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
