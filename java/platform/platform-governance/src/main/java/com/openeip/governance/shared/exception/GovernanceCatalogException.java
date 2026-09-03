package com.openeip.governance.shared.exception;

/** Stable errors for governed provider and model lifecycle operations. */
public class GovernanceCatalogException extends RuntimeException {
  public static final String CONFLICT_CODE = "GOV-C-001";
  public static final String TRANSITION_CODE = "GOV-C-002";
  public static final String VALIDATION_CODE = "GOV-V-001";
  public static final String BUDGET_CODE = "GOV-B-001";

  private final String code;

  private GovernanceCatalogException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static GovernanceCatalogException conflict(String message) {
    return new GovernanceCatalogException(CONFLICT_CODE, message);
  }

  public static GovernanceCatalogException transition(String message) {
    return new GovernanceCatalogException(TRANSITION_CODE, message);
  }

  public static GovernanceCatalogException invalid(String message) {
    return new GovernanceCatalogException(VALIDATION_CODE, message);
  }

  public static GovernanceCatalogException budgetExceeded(String message) {
    return new GovernanceCatalogException(BUDGET_CODE, message);
  }

  public String code() {
    return code;
  }
}
