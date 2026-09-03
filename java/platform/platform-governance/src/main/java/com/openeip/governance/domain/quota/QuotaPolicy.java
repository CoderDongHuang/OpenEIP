package com.openeip.governance.domain.quota;

import java.time.Instant;
import java.util.UUID;

/** Immutable snapshot of a revisioned tenant quota policy. */
public record QuotaPolicy(
    UUID id,
    UUID tenantId,
    String name,
    String policyVersion,
    QuotaLimits limits,
    QuotaWindowType windowType,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
