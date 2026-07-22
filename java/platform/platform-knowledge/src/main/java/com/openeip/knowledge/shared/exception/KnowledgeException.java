package com.openeip.knowledge.shared.exception;

import com.openeip.knowledge.domain.ProcessingStatus;
import org.springframework.http.HttpStatus;

/** Stable knowledge error mapped without persistence or membership detail. */
public class KnowledgeException extends RuntimeException {
  private final String errorCode;
  private final HttpStatus httpStatus;

  private KnowledgeException(String code, HttpStatus status, String message) {
    super(message);
    this.errorCode = code;
    this.httpStatus = status;
  }

  public static KnowledgeException notFound() {
    return new KnowledgeException(
        "KNOW-E-001", HttpStatus.NOT_FOUND, "Knowledge resource not found");
  }

  public static KnowledgeException forbidden() {
    return new KnowledgeException(
        "KNOW-E-002", HttpStatus.FORBIDDEN, "Insufficient knowledge permission");
  }

  public static KnowledgeException invalid(String message) {
    return new KnowledgeException("KNOW-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static KnowledgeException conflict(String message) {
    return new KnowledgeException("KNOW-E-003", HttpStatus.CONFLICT, message);
  }

  public static KnowledgeException invalidTransition(ProcessingStatus from, ProcessingStatus to) {
    return conflict("Invalid processing transition " + from + " -> " + to);
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
