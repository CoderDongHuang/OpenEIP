package com.openeip.governance.application.budget;

import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.usage.UsageLedgerPort;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.budget.Budget;
import com.openeip.governance.domain.budget.BudgetDecision;
import com.openeip.governance.domain.budget.BudgetDecisionOutcome;
import com.openeip.governance.domain.budget.BudgetDecisionRegistration;
import com.openeip.governance.domain.budget.BudgetDecisionRequest;
import com.openeip.governance.domain.budget.BudgetDecisionResult;
import com.openeip.governance.domain.budget.BudgetDecisionType;
import com.openeip.governance.domain.budget.BudgetRegistration;
import com.openeip.governance.domain.budget.BudgetWindow;
import com.openeip.governance.domain.budget.BudgetWindowType;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for tenant budget authorization and monotonic reservations. */
@Service
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Budget, usage, and audit ports are application-scoped collaborators.")
public class BudgetService {
  private final BudgetPort budgets;
  private final UsageLedgerPort usage;
  private final AuditService audit;
  private final Clock clock;

  @Autowired
  public BudgetService(BudgetPort budgets, UsageLedgerPort usage, AuditService audit) {
    this(budgets, usage, audit, Clock.systemUTC());
  }

  BudgetService(BudgetPort budgets, UsageLedgerPort usage, AuditService audit, Clock clock) {
    this.budgets = budgets;
    this.usage = usage;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Budget create(BudgetRegistration registration) {
    Context context = context(registration.tenantId());
    try {
      Budget budget = budgets.create(registration);
      audit(context, "governance.budget.created", "budget", budget.id(), AuditOutcome.SUCCESS);
      return budget;
    } catch (GovernanceCatalogException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw GovernanceCatalogException.invalid("Budget creation is invalid");
    }
  }

  @Transactional(readOnly = true)
  public List<Budget> list(int limit) {
    Context context = context(null);
    return budgets.budgets(context.tenantId(), Math.min(Math.max(limit, 1), 100));
  }

  @Transactional
  public BudgetDecisionResult decide(BudgetDecisionRequest request) {
    Context context = context(request.tenantId());
    Budget budget = budgets.lockBudget(context.tenantId(), request.budgetId());
    BudgetWindow window = window(budget.windowType(), request.now());
    BudgetDecision previous =
        budgets.latestDecision(context.tenantId(), budget.id(), request.executionId()).orElse(null);
    validateTransition(request.type(), request.reservedAmount(), request.now(), previous);

    Instant from = budget.windowType() == BudgetWindowType.EXECUTION ? null : window.start();
    Instant to = budget.windowType() == BudgetWindowType.EXECUTION ? null : window.end();
    BigDecimal observed = usage.totalAmount(context.tenantId(), request.executionId(), from, to);
    BigDecimal otherReservations =
        budgets.latestAllowedDecisions(context.tenantId(), budget.id(), from, to).stream()
            .filter(decision -> !decision.executionId().equals(request.executionId()))
            .map(BudgetDecision::reservedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal total = observed.add(otherReservations).add(request.reservedAmount());
    BudgetDecisionOutcome outcome =
        total.compareTo(budget.limitAmount()) <= 0
            ? BudgetDecisionOutcome.ALLOW
            : BudgetDecisionOutcome.DENY;
    BudgetDecision decision =
        budgets.appendDecision(
            new BudgetDecisionRegistration(
                context.tenantId(),
                budget.id(),
                request.executionId(),
                request.type(),
                context.policyVersion(),
                observed,
                request.reservedAmount(),
                outcome,
                request.now()));
    audit(
        context,
        "governance.budget." + outcome.name().toLowerCase(),
        "budget",
        budget.id(),
        outcome == BudgetDecisionOutcome.ALLOW ? AuditOutcome.SUCCESS : AuditOutcome.DENIED);
    return new BudgetDecisionResult(
        decision,
        outcome == BudgetDecisionOutcome.ALLOW ? null : GovernanceCatalogException.BUDGET_CODE);
  }

  private void validateTransition(
      BudgetDecisionType type,
      BigDecimal reservedAmount,
      Instant requestedAt,
      BudgetDecision previous) {
    if (type == BudgetDecisionType.START && previous != null) {
      throw GovernanceCatalogException.conflict("Execution already has a budget decision");
    }
    if (type == BudgetDecisionType.CHECKPOINT) {
      if (previous == null || previous.decision() != BudgetDecisionOutcome.ALLOW) {
        throw GovernanceCatalogException.transition(
            "A checkpoint requires an existing allowed budget decision");
      }
      if (reservedAmount.compareTo(previous.reservedAmount()) > 0) {
        throw GovernanceCatalogException.transition(
            "A checkpoint cannot increase the execution reservation");
      }
      if (!requestedAt.isAfter(previous.createdAt())) {
        throw GovernanceCatalogException.transition(
            "A checkpoint must be later than the previous budget decision");
      }
    }
  }

  private BudgetWindow window(BudgetWindowType type, Instant now) {
    if (type == BudgetWindowType.EXECUTION) {
      return new BudgetWindow(type, Instant.MIN, Instant.MAX);
    }
    ZonedDateTime current = now.atZone(ZoneOffset.UTC);
    ZonedDateTime start;
    ZonedDateTime end;
    if (type == BudgetWindowType.DAILY) {
      start = current.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = start.plusDays(1);
    } else if (type == BudgetWindowType.WEEKLY) {
      start =
          current
              .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
              .toLocalDate()
              .atStartOfDay(ZoneOffset.UTC);
      end = start.plusWeeks(1);
    } else {
      start = current.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = start.plusMonths(1);
    }
    return new BudgetWindow(type, start.toInstant(), end.toInstant());
  }

  private Budget budget(UUID tenantId, UUID budgetId) {
    return budgets
        .budget(tenantId, budgetId)
        .orElseThrow(
            () -> GovernanceCatalogException.invalid("Budget was not found in this tenant"));
  }

  private Context context(UUID expectedTenantId) {
    var value = TenantContextHolder.required();
    Instant now = clock.instant();
    if (value.expiredAt(now)
        || (expectedTenantId != null && !expectedTenantId.equals(value.tenantId()))) {
      throw GovernanceCatalogException.invalid("Budget command does not match active context");
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
