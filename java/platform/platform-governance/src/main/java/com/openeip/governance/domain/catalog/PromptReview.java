package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.UUID;

/** Immutable review or evaluation evidence reference for a Prompt version. */
public record PromptReview(
    UUID id,
    UUID tenantId,
    UUID promptVersionId,
    UUID reviewerId,
    String decision,
    String reason,
    String policyVersion,
    UUID evaluationRunId,
    Instant createdAt) {}
