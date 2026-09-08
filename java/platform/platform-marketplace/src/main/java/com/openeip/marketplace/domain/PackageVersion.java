package com.openeip.marketplace.domain;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2", "EI_EXPOSE_REP"},
    justification = "The manifest is defensively copied at the immutable domain boundary.")
public record PackageVersion(
    UUID id,
    UUID tenantId,
    UUID packageId,
    String version,
    String artifactUri,
    String sha256,
    String runtime,
    Map<String, Object> manifest,
    PackageState state,
    long revision,
    String reviewNote,
    Instant createdAt,
    Instant reviewedAt,
    Instant publishedAt) {
  public PackageVersion {
    manifest = Map.copyOf(new LinkedHashMap<>(manifest));
  }
}
