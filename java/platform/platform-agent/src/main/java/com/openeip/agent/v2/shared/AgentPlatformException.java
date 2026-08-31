package com.openeip.agent.v2.shared;

import org.springframework.http.HttpStatus;

public final class AgentPlatformException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  private AgentPlatformException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static AgentPlatformException invalid(String message) {
    return new AgentPlatformException("AGENT2-V-001", message, HttpStatus.BAD_REQUEST);
  }

  public static AgentPlatformException forbidden() {
    return new AgentPlatformException(
        "AGENT2-P-001", "Agent resource is not accessible", HttpStatus.FORBIDDEN);
  }

  public static AgentPlatformException notFound() {
    return new AgentPlatformException(
        "AGENT2-N-001", "Agent resource was not found", HttpStatus.NOT_FOUND);
  }

  public static AgentPlatformException conflict(String message) {
    return new AgentPlatformException("AGENT2-C-001", message, HttpStatus.CONFLICT);
  }

  public static AgentPlatformException precondition(String message) {
    return new AgentPlatformException("AGENT2-C-002", message, HttpStatus.PRECONDITION_FAILED);
  }

  public static AgentPlatformException upstream() {
    return new AgentPlatformException(
        "AGENT2-S-001", "Agent execution failed", HttpStatus.BAD_GATEWAY);
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }
}
