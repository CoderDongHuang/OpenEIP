package com.openeip.governance.domain.catalog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Immutable model version used to make execution selection reproducible. */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Model version collections are immutable catalog snapshots.")
public record ModelVersion(
    UUID id,
    UUID tenantId,
    UUID modelId,
    int versionNumber,
    String contentDigest,
    Set<String> capabilities,
    Set<String> routingLabels,
    UUID pricingSnapshotId,
    Instant createdAt) {}
