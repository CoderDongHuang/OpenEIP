package com.openeip.connector.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ConnectorConfig(JsonNode values, Map<String, String> credentials) {
  public ConnectorConfig {
    values = values.deepCopy();
    credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
  }

  @Override
  public JsonNode values() {
    return values.deepCopy();
  }
}
