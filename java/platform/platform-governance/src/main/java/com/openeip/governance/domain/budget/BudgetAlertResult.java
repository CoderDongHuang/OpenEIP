package com.openeip.governance.domain.budget;

/** Result of an idempotent alert creation. */
public record BudgetAlertResult(BudgetAlert alert, boolean duplicate) {}
