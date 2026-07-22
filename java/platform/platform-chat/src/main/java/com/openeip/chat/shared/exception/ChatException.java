package com.openeip.chat.shared.exception;

import org.springframework.http.HttpStatus;

/** Stable Chat failure without session, membership, message, or provider detail. */
public class ChatException extends RuntimeException {
  private final String errorCode;
  private final HttpStatus httpStatus;

  private ChatException(String code, HttpStatus status, String message) {
    super(message);
    this.errorCode = code;
    this.httpStatus = status;
  }

  public static ChatException notFound() {
    return new ChatException("CHAT-E-001", HttpStatus.NOT_FOUND, "Chat session not found");
  }

  public static ChatException invalid(String message) {
    return new ChatException("CHAT-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static ChatException conflict(String message) {
    return new ChatException("CHAT-E-002", HttpStatus.CONFLICT, message);
  }

  public static ChatException upstream() {
    return new ChatException(
        "CHAT-S-001", HttpStatus.SERVICE_UNAVAILABLE, "Chat generation failed");
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
