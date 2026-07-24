package com.openeip.workflow.domain;

public enum ExecutionStatus {
  QUEUED,
  RUNNING,
  WAITING_APPROVAL,
  WAITING_DELAY,
  RETRY_WAIT,
  CANCELLING,
  SUCCEEDED,
  FAILED,
  CANCELLED;

  public boolean terminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED;
  }
}
