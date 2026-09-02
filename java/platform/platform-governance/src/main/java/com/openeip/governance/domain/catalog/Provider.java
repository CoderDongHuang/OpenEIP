package com.openeip.governance.domain.catalog;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persisted provider policy metadata. The secret value is intentionally absent. */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Provider collections are deserialized as immutable catalog snapshots.")
public record Provider(
    UUID id,
    UUID tenantId,
    String name,
    Map<String, Object> endpointPolicy,
    String secretRef,
    Set<String> capabilities,
    ProviderState state,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
