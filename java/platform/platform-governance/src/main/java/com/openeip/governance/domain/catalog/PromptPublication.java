package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.UUID;

/** Immutable publication reference; active is a projection for the current pointer. */
public record PromptPublication(
    UUID id,
    UUID tenantId,
    UUID promptId,
    UUID promptVersionId,
    String contentDigest,
    String publicationReason,
    String policyVersion,
    boolean active,
    UUID createdBy,
    Instant createdAt) {}
