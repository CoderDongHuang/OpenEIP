package com.openeip.marketplace.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

final class MarketplaceDtos {
  private MarketplaceDtos() {}

  record Page(List<?> items, String nextCursor) {
    Page {
      items = List.copyOf(items);
    }
  }

  record CreatePackageRequest(
      @NotBlank String type,
      @NotBlank @Size(max = 128) String slug,
      @NotBlank @Size(max = 128) String displayName,
      @NotBlank @Size(max = 2048) String description) {}

  record CreateVersionRequest(
      @NotBlank @Size(max = 32) String version,
      @NotBlank @Size(max = 1024) String artifactUri,
      @NotBlank String sha256,
      @NotBlank @Size(max = 64) String runtime,
      Map<String, Object> manifest) {}

  record ReviewRequest(@NotBlank @Size(max = 512) String note) {}
}
