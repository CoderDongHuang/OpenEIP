package com.openeip.workflow.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.workflow.shared.WorkflowException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.openeip.workflow")
public class WorkflowExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExceptionHandler.class);

  @ExceptionHandler(WorkflowException.class)
  public ResponseEntity<ApiEnvelope<Void>> workflow(
      WorkflowException exception, HttpServletRequest request) {
    return error(
        exception.getStatus().value(), exception.getCode(), exception.getMessage(), request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MissingRequestHeaderException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "WF-V-001", "Invalid workflow request", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiEnvelope<Void>> conflict(
      Exception exception, HttpServletRequest request) {
    return error(409, "WF-E-003", "Workflow resource conflicts with existing data", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> unexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled workflow error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName(),
        exception);
    return error(500, "WF-S-001", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
