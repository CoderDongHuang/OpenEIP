package com.openeip.marketplace.domain;

import java.time.Instant;
import java.util.UUID;

public record MarketplacePackage(
    UUID id,
    UUID tenantId,
    PackageType type,
    String slug,
    String displayName,
    String description,
    String publisher,
    PackageState state,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
