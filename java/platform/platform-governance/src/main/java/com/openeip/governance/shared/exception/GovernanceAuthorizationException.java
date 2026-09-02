package com.openeip.governance.shared.exception;

/** Stable fail-closed authorization error for missing or invalid governance context. */
public class GovernanceAuthorizationException extends RuntimeException {
  public static final String CONTEXT_ERROR_CODE = "GOV-A-001";
  public static final String SYSTEM_SCOPE_ERROR_CODE = "GOV-A-002";

  private final String code;

  public GovernanceAuthorizationException(String message) {
    this(CONTEXT_ERROR_CODE, message);
  }

  private GovernanceAuthorizationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static GovernanceAuthorizationException invalidContext(String message) {
    return new GovernanceAuthorizationException(CONTEXT_ERROR_CODE, message);
  }

  public static GovernanceAuthorizationException systemScopeNotPermitted() {
    return new GovernanceAuthorizationException(
        SYSTEM_SCOPE_ERROR_CODE, "System scope is not permitted");
  }

  public String code() {
    return code;
  }
}
