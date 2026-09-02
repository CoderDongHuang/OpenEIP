package com.openeip.governance.domain.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Input for a Prompt definition and its first immutable version. */
public record PromptRegistration(
    UUID tenantId,
    String name,
    String purpose,
    String content,
    String compatibilityVersion,
    UUID createdBy,
    Instant now) {

  public PromptRegistration {
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(createdBy, "createdBy is required");
    Objects.requireNonNull(now, "now is required");
    name = bounded(name, "name", 128);
    purpose = bounded(purpose, "purpose", 128);
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
