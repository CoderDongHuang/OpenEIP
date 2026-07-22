package com.openeip.agent.shared.exception;

import org.springframework.http.HttpStatus;

/** Stable Agent failure without prompt, arguments, observation, secret, or provider detail. */
public class AgentException extends RuntimeException {
  private final String errorCode;
  private final HttpStatus httpStatus;

  private AgentException(String code, HttpStatus status, String message) {
    super(message);
    this.errorCode = code;
    this.httpStatus = status;
  }

  public static AgentException notFound() {
    return new AgentException("AGENT-N-001", HttpStatus.NOT_FOUND, "Agent not found");
  }

  public static AgentException invalid(String message) {
    return new AgentException("AGENT-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static AgentException upstream() {
    return new AgentException(
        "AGENT-S-001", HttpStatus.SERVICE_UNAVAILABLE, "Agent execution failed");
  }

  public String getErrorCode() {
    return errorCode;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
