package com.openeip.governance.domain.context;

/** Identifies whether a governance operation runs for a tenant or an audited system scope. */
public enum GovernanceScope {
  TENANT,
  SYSTEM
}
