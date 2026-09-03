package com.openeip.governance.application.budget;

import com.openeip.governance.domain.budget.Budget;
import com.openeip.governance.domain.budget.BudgetDecision;
import com.openeip.governance.domain.budget.BudgetDecisionRegistration;
import com.openeip.governance.domain.budget.BudgetRegistration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for tenant budgets and append-only execution decisions. */
public interface BudgetPort {
  Budget create(BudgetRegistration registration);

  Optional<Budget> budget(UUID tenantId, UUID budgetId);

  Budget lockBudget(UUID tenantId, UUID budgetId);

  List<Budget> budgets(UUID tenantId, int limit);

  Optional<BudgetDecision> latestDecision(UUID tenantId, UUID budgetId, UUID executionId);

  List<BudgetDecision> latestAllowedDecisions(
      UUID tenantId, UUID budgetId, Instant from, Instant to);

  BudgetDecision appendDecision(BudgetDecisionRegistration registration);
}
