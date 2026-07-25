package com.openeip.connector.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.connector.shared.ConnectorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.openeip.connector")
public class ConnectorExceptionHandler {
  @ExceptionHandler(ConnectorException.class)
  public ResponseEntity<ApiEnvelope<Void>> connector(
      ConnectorException exception, HttpServletRequest request) {
    return error(
        exception.getStatus().value(), exception.getCode(), exception.getMessage(), request);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "CONN-V-001", "Invalid connector request", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
