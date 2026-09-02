package com.openeip.governance.domain.catalog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.UUID;

/** Immutable Prompt version metadata; plaintext content is never exposed by this record. */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Prompt version contains immutable scalar metadata only.")
public record PromptVersion(
    UUID id,
    UUID tenantId,
    UUID promptId,
    int versionNumber,
    String contentCiphertext,
    String contentDigest,
    String compatibilityVersion,
    UUID createdBy,
    Instant createdAt) {}
