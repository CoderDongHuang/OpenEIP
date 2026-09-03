package com.openeip.governance.domain.usage;

/** Result of an idempotent usage append, identifying whether the fact already existed. */
public record UsageAppendResult(UsageRecord record, boolean duplicate) {}
