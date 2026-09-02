package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.UUID;

/** Mutable model registration whose execution choices are pinned to immutable versions. */
public record Model(
    UUID id,
    UUID tenantId,
    UUID providerId,
    String name,
    ModelState state,
    String policyVersion,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
