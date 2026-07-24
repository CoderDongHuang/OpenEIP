package com.openeip.workflow.domain;

public enum WorkflowRole {
  OWNER,
  EDITOR,
  RUNNER,
  APPROVER,
  VIEWER;

  public boolean canEdit() {
    return this == OWNER || this == EDITOR;
  }

  public boolean canRun() {
    return canEdit() || this == RUNNER;
  }

  public boolean canApprove() {
    return this == OWNER || this == APPROVER;
  }
}
