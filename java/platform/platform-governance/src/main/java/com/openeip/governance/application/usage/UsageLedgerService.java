package com.openeip.governance.application.usage;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.usage.PricingSnapshot;
import com.openeip.governance.domain.usage.UsageAppendResult;
import com.openeip.governance.domain.usage.UsageRecord;
import com.openeip.governance.domain.usage.UsageRegistration;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for idempotent usage facts and reproducible historical cost. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Ledger, pricing, and audit ports are application-scoped collaborators.")
public class UsageLedgerService {
  private static final int AMOUNT_SCALE = 6;
  private final UsageLedgerPort ledger;
  private final PricingSnapshotPort pricing;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public UsageLedgerService(
      UsageLedgerPort ledger, PricingSnapshotPort pricing, AuditService audit) {
    this(ledger, pricing, audit, Clock.systemUTC());
  }

  UsageLedgerService(
      UsageLedgerPort ledger, PricingSnapshotPort pricing, AuditService audit, Clock clock) {
    this.ledger = ledger;
    this.pricing = pricing;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public UsageAppendResult append(UsageRegistration registration) {
    Context context = context(registration.tenantId());
    if (!context.requestId().equals(registration.requestId())
        || !context.traceId().equals(registration.traceId())) {
      throw GovernanceCatalogException.invalid("Usage correlation does not match active context");
    }
    PricingSnapshot snapshot =
        pricing
            .pricingSnapshot(context.tenantId(), registration.pricingSnapshotId())
            .orElseThrow(
                () ->
                    GovernanceCatalogException.invalid(
                        "Pricing snapshot was not found in this tenant"));
    try {
      BigDecimal amount = calculate(registration, snapshot);
      UsageAppendResult result = ledger.append(registration, snapshot, amount);
      if (!result.duplicate()) {
        audit(context, result.record());
      }
      return result;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Usage record is invalid");
    }
  }

  @Transactional(readOnly = true)
  public List<UsageRecord> list(UUID executionId, Instant from, Instant to, int limit) {
    Context context = context(null);
    if (from != null && to != null && from.isAfter(to)) {
      throw GovernanceCatalogException.invalid("Usage time range is invalid");
    }
    return ledger.usages(
        context.tenantId(), executionId, from, to, Math.min(Math.max(limit, 1), 100));
  }

  private BigDecimal calculate(UsageRegistration registration, PricingSnapshot snapshot) {
    try {
      RoundingMode mode = RoundingMode.valueOf(snapshot.roundingMode());
      return snapshot
          .inputUnitPrice()
          .multiply(BigDecimal.valueOf(registration.inputUnits()))
          .add(snapshot.outputUnitPrice().multiply(BigDecimal.valueOf(registration.outputUnits())))
          .setScale(AMOUNT_SCALE, mode);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      throw GovernanceCatalogException.invalid("Usage cost cannot be calculated");
    }
  }

  private void audit(Context context, UsageRecord record) {
    audit.append(
        AuditService.command(
            UUID.randomUUID(),
            context.tenantId(),
            context.principalId(),
            "governance.usage.recorded",
            "usage",
            record.id().toString(),
            AuditOutcome.SUCCESS,
            context.requestId(),
            context.traceId(),
            context.policyVersion(),
            clock.instant(),
            Map.of("usageRevision", record.usageRevision())));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Usage command does not match active context");
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
