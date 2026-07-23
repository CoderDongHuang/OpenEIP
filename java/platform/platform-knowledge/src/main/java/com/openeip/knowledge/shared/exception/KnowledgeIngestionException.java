package com.openeip.knowledge.shared.exception;

import org.springframework.http.HttpStatus;

/** Stable ingestion failure without upstream body, credential, or file-content detail. */
public class KnowledgeIngestionException extends RuntimeException {
  private final String errorCode;
  private final String failureCode;
  private final HttpStatus httpStatus;

  private KnowledgeIngestionException(
      String errorCode, String failureCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.failureCode = failureCode;
    this.httpStatus = httpStatus;
  }

  public static KnowledgeIngestionException unsupported() {
    return new KnowledgeIngestionException(
        "KNOW-V-002",
        "INGEST.UNSUPPORTED",
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Document type is not processable");
  }

  public static KnowledgeIngestionException corrupted() {
    return new KnowledgeIngestionException(
        "KNOW-S-002",
        "INGEST.STORAGE",
        HttpStatus.SERVICE_UNAVAILABLE,
        "Document content is unavailable");
  }

  public static KnowledgeIngestionException upstream() {
    return new KnowledgeIngestionException(
        "KNOW-S-003",
        "INGEST.UPSTREAM",
        HttpStatus.SERVICE_UNAVAILABLE,
        "Document processing is unavailable");
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
