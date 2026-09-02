package com.openeip.governance.domain.audit;

/** Result of an append, including whether the event was safely deduplicated. */
public record AuditAppendResult(AuditRecord record, boolean duplicate) {}
