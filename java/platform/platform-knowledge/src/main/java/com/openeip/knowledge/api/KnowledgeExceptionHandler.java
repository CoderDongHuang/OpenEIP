package com.openeip.knowledge.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.knowledge.shared.exception.KnowledgeException;
import com.openeip.knowledge.shared.exception.KnowledgeIngestionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.openeip.knowledge")
public class KnowledgeExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeExceptionHandler.class);

  @ExceptionHandler(KnowledgeException.class)
  public ResponseEntity<ApiEnvelope<Void>> handle(
      KnowledgeException exception, HttpServletRequest request) {
    return error(
        exception.getHttpStatus().value(),
        exception.getErrorCode(),
        exception.getMessage(),
        request);
  }

  @ExceptionHandler(KnowledgeIngestionException.class)
  public ResponseEntity<ApiEnvelope<Void>> ingestion(
      KnowledgeIngestionException exception, HttpServletRequest request) {
    return error(
        exception.getHttpStatus().value(),
        exception.getErrorCode(),
        exception.getMessage(),
        request);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "KNOW-V-001", "Invalid knowledge request", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiEnvelope<Void>> conflict(
      Exception exception, HttpServletRequest request) {
    return error(409, "KNOW-E-003", "Knowledge resource conflicts with existing data", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> unexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled knowledge error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName());
    return error(500, "KNOW-S-001", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
