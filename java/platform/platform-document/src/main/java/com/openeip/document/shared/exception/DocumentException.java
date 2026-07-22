package com.openeip.document.shared.exception;

import org.springframework.http.HttpStatus;

/** Stable document error mapped to the public API envelope. */
public class DocumentException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  private DocumentException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public static DocumentException notFound() {
    return new DocumentException("DOC-E-001", HttpStatus.NOT_FOUND, "File not found");
  }

  public static DocumentException invalid(String message) {
    return new DocumentException("DOC-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static DocumentException unsupported() {
    return new DocumentException(
        "DOC-V-002", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type");
  }

  public static DocumentException tooLarge(long maxBytes) {
    return new DocumentException(
        "DOC-V-003", HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + maxBytes + " bytes");
  }

  public static DocumentException storageUnavailable() {
    return new DocumentException(
        "DOC-S-001", HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable");
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
