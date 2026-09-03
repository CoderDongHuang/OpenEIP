package com.openeip.governance.application.budget;

import com.openeip.governance.domain.budget.BudgetAlert;
import com.openeip.governance.domain.budget.BudgetAlertRegistration;
import com.openeip.governance.domain.budget.BudgetAlertResult;
import com.openeip.governance.domain.budget.BudgetAlertStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for idempotent budget threshold alerts. */
public interface BudgetAlertPort {
  BudgetAlertResult create(BudgetAlertRegistration registration);

  Optional<BudgetAlert> alert(UUID tenantId, UUID alertId);

  Optional<BudgetAlert> alertByIdempotency(
      UUID tenantId,
      UUID budgetId,
      Instant windowStart,
      java.math.BigDecimal threshold,
      long crossingRevision);

  boolean updateStatus(
      UUID tenantId, UUID alertId, BudgetAlertStatus expectedStatus, BudgetAlertStatus newStatus);
}
