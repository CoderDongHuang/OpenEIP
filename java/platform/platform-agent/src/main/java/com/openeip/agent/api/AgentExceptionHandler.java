package com.openeip.agent.api;

import com.openeip.agent.shared.exception.AgentException;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.openeip.agent")
public class AgentExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AgentExceptionHandler.class);

  @ExceptionHandler(AgentException.class)
  public ResponseEntity<ApiEnvelope<Void>> handle(
      AgentException exception, HttpServletRequest request) {
    return error(
        exception.getHttpStatus().value(),
        exception.getErrorCode(),
        exception.getMessage(),
        request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "AGENT-V-001", "Invalid Agent request", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> unexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled Agent error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName());
    return error(500, "AGENT-S-002", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
