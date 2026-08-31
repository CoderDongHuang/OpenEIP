package com.openeip.agent.v2.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openeip.agent.v2.shared.AgentPlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class AgentPlatformSupport {
  private AgentPlatformSupport() {}

  static String requiredText(String value, String field, int max) {
    String result = value == null ? "" : value.trim();
    if (result.isEmpty() || result.length() > max || forbiddenControl(result)) {
      throw AgentPlatformException.invalid("Invalid " + field);
    }
    return result;
  }

  static String optionalText(String value, String field, int max) {
    String result = value == null ? "" : value.trim();
    if (result.length() > max || forbiddenControl(result)) {
      throw AgentPlatformException.invalid("Invalid " + field);
    }
    return result;
  }

  static String uuid(String value) {
    try {
      if (value == null || !UUID.fromString(value).toString().equals(value)) {
        throw AgentPlatformException.invalid("Invalid resource identifier");
      }
      return value;
    } catch (IllegalArgumentException exception) {
      throw AgentPlatformException.invalid("Invalid resource identifier");
    }
  }

  static long revision(String value) {
    try {
      if (value == null || !value.matches("^(?:W/)?\"?[0-9]+\"?$")) {
        throw AgentPlatformException.precondition("A valid If-Match revision is required");
      }
      return Long.parseLong(value.replace("W/", "").replace("\"", ""));
    } catch (NumberFormatException exception) {
      throw AgentPlatformException.precondition("A valid If-Match revision is required");
    }
  }

  static String idempotencyKey(String value) {
    if (value == null
        || value.length() < 16
        || value.length() > 128
        || !value.matches("^[A-Za-z0-9._:-]+$")) {
      throw AgentPlatformException.invalid("A valid Idempotency-Key is required");
    }
    return value;
  }

  static int limit(int value) {
    if (value < 1 || value > 100) {
      throw AgentPlatformException.invalid("Limit must be between 1 and 100");
    }
    return value;
  }

  static String canonical(ObjectMapper mapper, JsonNode value) {
    try {
      return mapper.writeValueAsString(sorted(mapper, value));
    } catch (Exception exception) {
      throw AgentPlatformException.invalid("JSON value is not serializable");
    }
  }

  static JsonNode read(ObjectMapper mapper, String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Stored Agent JSON is invalid", exception);
    }
  }

  public static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  static void requireAllowedFields(JsonNode value, Set<String> allowed) {
    if (value == null || !value.isObject()) {
      throw AgentPlatformException.invalid("Request body must be an object");
    }
    Iterator<String> names = value.fieldNames();
    while (names.hasNext()) {
      if (!allowed.contains(names.next())) {
        throw AgentPlatformException.invalid("Unknown request field");
      }
    }
  }

  static List<String> stringList(JsonNode value, int max, String field) {
    if (value == null || !value.isArray() || value.isEmpty() || value.size() > max) {
      throw AgentPlatformException.invalid("Invalid " + field);
    }
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 256) {
        throw AgentPlatformException.invalid("Invalid " + field);
      }
      if (!result.add(item.textValue())) {
        throw AgentPlatformException.invalid("Duplicate " + field);
      }
    }
    return List.copyOf(result);
  }

  static Instant instant(String value, String field) {
    try {
      return value == null ? null : Instant.parse(value);
    } catch (Exception exception) {
      throw AgentPlatformException.invalid("Invalid " + field);
    }
  }

  private static JsonNode sorted(ObjectMapper mapper, JsonNode value) {
    if (value == null || value.isNull()) {
      return mapper.nullNode();
    }
    if (value.isObject()) {
      ObjectNode result = mapper.createObjectNode();
      Map<String, JsonNode> fields = new TreeMap<>();
      value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
      fields.forEach((key, child) -> result.set(key, sorted(mapper, child)));
      return result;
    }
    if (value.isArray()) {
      ArrayNode result = mapper.createArrayNode();
      value.forEach(child -> result.add(sorted(mapper, child)));
      return result;
    }
    return value.deepCopy();
  }

  private static boolean forbiddenControl(String value) {
    return value
        .chars()
        .anyMatch(
            character ->
                character < 32 && character != '\n' && character != '\r' && character != '\t');
  }
}
