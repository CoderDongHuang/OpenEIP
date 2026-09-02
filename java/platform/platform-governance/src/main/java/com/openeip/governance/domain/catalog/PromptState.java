package com.openeip.governance.domain.catalog;

/** Derived Prompt lifecycle state exposed by the governance application. */
public enum PromptState {
  DRAFT,
  IN_REVIEW,
  EVALUATED,
  PUBLISHED,
  DEPRECATED
}
