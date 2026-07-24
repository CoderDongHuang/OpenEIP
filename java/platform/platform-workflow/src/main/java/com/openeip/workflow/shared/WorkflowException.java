package com.openeip.workflow.shared;

import org.springframework.http.HttpStatus;

public class WorkflowException extends RuntimeException {
  private final String code;
  private final HttpStatus status;

  public WorkflowException(String code, HttpStatus status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static WorkflowException invalid(String message) {
    return new WorkflowException("WF-V-001", HttpStatus.BAD_REQUEST, message);
  }

  public static WorkflowException notFound() {
    return new WorkflowException("WF-E-001", HttpStatus.NOT_FOUND, "Workflow resource not found");
  }

  public static WorkflowException forbidden() {
    return new WorkflowException("WF-E-002", HttpStatus.FORBIDDEN, "Workflow operation forbidden");
  }

  public static WorkflowException conflict(String message) {
    return new WorkflowException("WF-E-003", HttpStatus.CONFLICT, message);
  }

  public static WorkflowException unauthorized() {
    return new WorkflowException("WF-A-001", HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
  }

  public static WorkflowException rateLimited() {
    return new WorkflowException(
        "WF-A-002", HttpStatus.TOO_MANY_REQUESTS, "Webhook rate limit exceeded");
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
