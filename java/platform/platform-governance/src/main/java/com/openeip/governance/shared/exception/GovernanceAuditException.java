package com.openeip.governance.shared.exception;

/** Stable errors for audit integrity and event idempotency boundaries. */
public class GovernanceAuditException extends RuntimeException {
  public static final String INTEGRITY_ERROR_CODE = "GOV-S-001";
  public static final String IDEMPOTENCY_CONFLICT_CODE = "GOV-I-001";
  public static final String VALIDATION_ERROR_CODE = "GOV-V-001";

  private final String code;

  private GovernanceAuditException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static GovernanceAuditException integrity(String message) {
    return new GovernanceAuditException(INTEGRITY_ERROR_CODE, message);
  }

  public static GovernanceAuditException conflict(String message) {
    return new GovernanceAuditException(IDEMPOTENCY_CONFLICT_CODE, message);
  }

  public static GovernanceAuditException invalid(String message) {
    return new GovernanceAuditException(VALIDATION_ERROR_CODE, message);
  }

  public String code() {
    return code;
  }
}
