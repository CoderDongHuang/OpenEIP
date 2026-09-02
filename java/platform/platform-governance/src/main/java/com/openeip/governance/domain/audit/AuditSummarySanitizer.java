package com.openeip.governance.domain.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Enforces the small, content-free audit summary contract before serialization. */
public final class AuditSummarySanitizer {
  private static final int MAX_PROPERTIES = 16;
  private static final int MAX_STRING_LENGTH = 512;
  private static final Set<String> ALLOWED_KEYS =
      Set.of(
          "itemCount",
          "reasonCode",
          "policyDecision",
          "versionId",
          "digest",
          "usageRevision",
          "durationMs",
          "outcomeCode",
          "connectorType",
          "modelName",
          "promptVersionId",
          "budgetWindow",
          "threshold");

  private AuditSummarySanitizer() {}

  public static Map<String, Object> sanitize(Map<String, Object> input) {
    if (input == null || input.size() > MAX_PROPERTIES) {
      throw new IllegalArgumentException("Audit summary is missing or too large");
    }
    var result = new LinkedHashMap<String, Object>();
    for (var entry : input.entrySet()) {
      String key = entry.getKey();
      if (key == null || !ALLOWED_KEYS.contains(key)) {
        throw new IllegalArgumentException("Audit summary field is not allowlisted");
      }
      Object value = entry.getValue();
      if (value == null || value instanceof Boolean || value instanceof Number) {
        if (value instanceof Double || value instanceof Float) {
          if (!Double.isFinite(((Number) value).doubleValue())) {
            throw new IllegalArgumentException("Audit summary number must be finite");
          }
        }
        result.put(key, value);
      } else if (value instanceof String text && text.length() <= MAX_STRING_LENGTH) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("authorization:")
            || lower.contains("bearer ")
            || lower.contains("-----begin ")
            || lower.contains("private reasoning")) {
          throw new IllegalArgumentException("Sensitive audit summary content is not permitted");
        }
        result.put(key, text);
      } else {
        throw new IllegalArgumentException("Audit summary values must be bounded scalars");
      }
    }
    return Collections.unmodifiableMap(result);
  }
}
