package com.openeip.auth.api.advice;

import com.openeip.auth.shared.exception.AuthException;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps application and validation failures to the documented error envelope. */
@RestControllerAdvice(basePackages = "com.openeip.auth")
public class AuthExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuthExceptionHandler.class);

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleAuth(
      AuthException exception, HttpServletRequest request) {
    return error(
        exception.getHttpStatus(), exception.getErrorCode(), exception.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
    return error(400, "AUTH-V-001", message, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleUnreadable(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    return error(400, "AUTH-V-001", "Malformed JSON request", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleConflict(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    return error(409, "AUTH-E-004", "Resource already exists or is still referenced", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled Auth error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName());
    return error(500, "AUTH-S-001", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
