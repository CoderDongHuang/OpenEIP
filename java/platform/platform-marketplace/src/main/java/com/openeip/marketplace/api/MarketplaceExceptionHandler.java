package com.openeip.marketplace.api;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.marketplace.shared.MarketplaceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.openeip.marketplace")
public class MarketplaceExceptionHandler {
  @ExceptionHandler(MarketplaceException.class)
  ResponseEntity<ApiEnvelope<Void>> marketplace(
      MarketplaceException exception, HttpServletRequest request) {
    int status =
        exception.code().equals("MKT-A-001")
            ? 403
            : exception.code().equals("MKT-C-001")
                ? 409
                : exception.code().equals("MKT-C-002") ? 422 : 400;
    return error(status, exception.code(), exception.getMessage(), request);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  ResponseEntity<ApiEnvelope<Void>> invalid(Exception exception, HttpServletRequest request) {
    return error(400, "MKT-V-001", "Invalid Marketplace request", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
