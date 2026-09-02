package com.openeip.governance.domain.catalog;

/** Lifecycle state for a governed model registration. */
public enum ModelState {
  DRAFT,
  REVIEWED,
  ENABLED,
  SUSPENDED,
  DEPRECATED
}
