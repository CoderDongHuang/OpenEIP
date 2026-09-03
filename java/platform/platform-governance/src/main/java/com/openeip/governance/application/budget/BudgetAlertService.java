package com.openeip.governance.application.budget;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.budget.BudgetAlert;
import com.openeip.governance.domain.budget.BudgetAlertRegistration;
import com.openeip.governance.domain.budget.BudgetAlertResult;
import com.openeip.governance.domain.budget.BudgetAlertStatus;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for tenant-scoped, monotonic alert delivery state. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Alert, budget, and audit ports are application-scoped collaborators.")
public class BudgetAlertService {
  private final BudgetPort budgets;
  private final BudgetAlertPort alerts;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public BudgetAlertService(BudgetPort budgets, BudgetAlertPort alerts, AuditService audit) {
    this(budgets, alerts, audit, Clock.systemUTC());
  }

  BudgetAlertService(BudgetPort budgets, BudgetAlertPort alerts, AuditService audit, Clock clock) {
    this.budgets = budgets;
    this.alerts = alerts;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public BudgetAlertResult create(BudgetAlertRegistration registration) {
    Context context = context(registration.tenantId());
    budget(context.tenantId(), registration.budgetId());
    try {
      BudgetAlertResult result = alerts.create(registration);
      if (!result.duplicate()) {
        audit(
            context,
            "governance.budget.alert.created",
            "budget-alert",
            result.alert().id(),
            AuditOutcome.SUCCESS);
      }
      return result;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Budget alert creation is invalid");
    }
  }

  @Transactional
  public BudgetAlert markSent(UUID alertId) {
    return transition(alertId, BudgetAlertStatus.PENDING, BudgetAlertStatus.SENT);
  }

  @Transactional
  public BudgetAlert acknowledge(UUID alertId) {
    return transition(alertId, BudgetAlertStatus.SENT, BudgetAlertStatus.ACKNOWLEDGED);
  }

  @Transactional(readOnly = true)
  public BudgetAlert get(UUID alertId) {
    Context context = context(null);
    return alerts
        .alert(context.tenantId(), alertId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Budget alert was not found in this tenant"));
  }

  private BudgetAlert transition(
      UUID alertId, BudgetAlertStatus expectedStatus, BudgetAlertStatus newStatus) {
    Context context = context(null);
    BudgetAlert current = get(alertId);
    if (current.status() != expectedStatus) {
      throw GovernanceCatalogException.transition("Budget alert status transition is invalid");
    }
    if (!alerts.updateStatus(context.tenantId(), alertId, expectedStatus, newStatus)) {
      throw GovernanceCatalogException.conflict("Budget alert status is stale");
    }
    BudgetAlert updated = get(alertId);
    audit(
        context,
        "governance.budget.alert." + newStatus.name().toLowerCase(),
        "budget-alert",
        alertId,
        AuditOutcome.SUCCESS);
    return updated;
  }

  private com.openeip.governance.domain.budget.Budget budget(UUID tenantId, UUID budgetId) {
    return budgets
        .budget(tenantId, budgetId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Budget was not found in this tenant"));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    if (value.expiredAt(clock.instant())
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid(
          "Budget alert command does not match active context");
    }
    return new Context(
        value.tenantId(),
        value.principalId(),
        value.requestId(),
        value.traceId(),
        value.policyVersion());
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

  private record Context(
      UUID tenantId, UUID principalId, String requestId, String traceId, String policyVersion) {}
}
