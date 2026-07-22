package com.openeip.knowledge.domain;

/** Persisted document processing lifecycle. */
public enum ProcessingStatus {
  PENDING_PARSE,
  PARSED,
  PENDING_EMBEDDING,
  READY,
  FAILED
}
