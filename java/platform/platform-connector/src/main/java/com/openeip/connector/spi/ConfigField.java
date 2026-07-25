package com.openeip.connector.spi;

import java.util.List;

public record ConfigField(
    String name,
    String label,
    FieldType type,
    boolean required,
    boolean secret,
    String defaultValue,
    List<String> options) {
  public ConfigField {
    options = options == null ? List.of() : List.copyOf(options);
  }

  public enum FieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    SELECT,
    URL
  }
}
