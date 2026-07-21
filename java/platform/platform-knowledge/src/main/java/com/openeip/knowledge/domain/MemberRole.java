package com.openeip.knowledge.domain;

/** Knowledge-base membership ordered by write capability. */
public enum MemberRole {
  VIEWER,
  EDITOR,
  OWNER;

  public boolean canEdit() {
    return this == EDITOR || this == OWNER;
  }
}
