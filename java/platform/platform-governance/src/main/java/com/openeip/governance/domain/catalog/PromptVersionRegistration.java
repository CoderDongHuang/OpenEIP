package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input for a new immutable Prompt version. */
public record PromptVersionRegistration(
    UUID tenantId,
    UUID promptId,
    String content,
    String compatibilityVersion,
    UUID createdBy,
    Instant now) {

  public PromptVersionRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(promptId, "promptId is required");
    Objects.requireNonNull(createdBy, "createdBy is required");
    Objects.requireNonNull(now, "now is required");
    content = bounded(content, "content", 65536);
    compatibilityVersion = bounded(compatibilityVersion, "compatibilityVersion", 64);
  }

  private static String bounded(String value, String field, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(field + " is required and bounded");
    }
    return value;
  }
}
