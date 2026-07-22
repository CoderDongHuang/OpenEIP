package com.openeip.document.api.advice;

import com.openeip.common.api.ApiEnvelope;
import com.openeip.common.web.RequestIdFilter;
import com.openeip.document.shared.exception.DocumentException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Maps document failures without exposing object paths or persistence internals. */
@RestControllerAdvice(basePackages = "com.openeip.document")
public class DocumentExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentExceptionHandler.class);

  @ExceptionHandler(DocumentException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleDocument(
      DocumentException exception, HttpServletRequest request) {
    return error(
        exception.getHttpStatus().value(),
        exception.getErrorCode(),
        exception.getMessage(),
        request);
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MissingServletRequestPartException.class
  })
  public ResponseEntity<ApiEnvelope<Void>> handleInvalid(
      Exception exception, HttpServletRequest request) {
    return error(400, "DOC-V-001", "Invalid file request", request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleMultipartLimit(
      MaxUploadSizeExceededException exception, HttpServletRequest request) {
    return error(413, "DOC-V-003", "File exceeds the configured request limit", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled document error requestId={} type={}",
        RequestIdFilter.get(request),
        exception.getClass().getName());
    return error(500, "DOC-S-002", "Internal server error", request);
  }

  private static ResponseEntity<ApiEnvelope<Void>> error(
      int status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiEnvelope.error(code, message, RequestIdFilter.get(request)));
  }
}
