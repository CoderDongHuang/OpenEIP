package com.openeip.governance.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import com.openeip.governance.shared.exception.GovernanceAuthorizationException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.openeip.governance")
public class GovernanceExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(GovernanceExceptionHandler.class);

  @ExceptionHandler(GovernanceAuthorizationException.class)
  public ResponseEntity<ApiEnvelope<Void>> authorization(
      GovernanceAuthorizationException exception, HttpServletRequest request) {
    return error(403, exception.code(), exception.getMessage(), request);
  }

  @ExceptionHandler(GovernanceCatalogException.class)
  public ResponseEntity<ApiEnvelope<Void>> catalog(
      GovernanceCatalogException exception, HttpServletRequest request) {
    int status =
        GovernanceCatalogException.CONFLICT_CODE.equals(exception.code())
                || GovernanceCatalogException.IDEMPOTENCY_CODE.equals(exception.code())
            ? 409
            : GovernanceCatalogException.TRANSITION_CODE.equals(exception.code())
                    || GovernanceCatalogException.BUDGET_CODE.equals(exception.code())
                ? 422
                : 400;
    return error(status, exception.code(), exception.getMessage(), request);
  }

  @ExceptionHandler(GovernanceAuditException.class)
  public ResponseEntity<ApiEnvelope<Void>> audit(
      GovernanceAuditException exception, HttpServletRequest request) {
    int status =
        GovernanceAuditException.IDEMPOTENCY_CONFLICT_CODE.equals(exception.code()) ? 409 : 422;
    return error(status, exception.code(), exception.getMessage(), request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MissingRequestHeaderException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "GOV-V-001", "Invalid Governance request", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> unexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled Governance error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName(),
        exception);
    return error(500, "GOV-S-001", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
