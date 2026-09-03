package com.openeip.governance.domain.budget;

/** Result of an authoritative budget decision. A denial is retained as history. */
public record BudgetDecisionResult(BudgetDecision decision, String errorCode) {
  public boolean allowed() {
    return decision.decision() == BudgetDecisionOutcome.ALLOW;
  }
}
