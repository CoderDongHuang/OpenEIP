package com.openeip.agent.v2.api;

import com.openeip.agent.v2.shared.AgentPlatformException;
import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(basePackages = "com.openeip.agent.v2")
public class AgentPlatformV2ExceptionHandler {
  @ExceptionHandler(AgentPlatformException.class)
  public ResponseEntity<ApiEnvelope<Void>> handle(
      AgentPlatformException exception, HttpServletRequest request) {
    return ResponseEntity.status(exception.status())
        .body(
            ApiEnvelope.error(
                exception.code(), exception.getMessage(), RequestIdFilter.get(request)));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> invalid(
      Exception exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ApiEnvelope.error(
                "AGENT2-V-001", "Invalid Agent request", RequestIdFilter.get(request)));
  }
}
