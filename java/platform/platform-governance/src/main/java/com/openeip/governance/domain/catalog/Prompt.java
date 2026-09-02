package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.UUID;

/** Prompt definition with a server-maintained publication pointer. */
public record Prompt(
    UUID id,
    UUID tenantId,
    String name,
    String purpose,
    UUID activePublicationId,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    PromptState state) {}
