package com.openeip.connector.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@FunctionalInterface
public interface DataReader {
  ReadResult read(ReadRequest request);

  record ReadRequest(String resource, JsonNode query, int limit) {}

  record ReadResult(List<JsonNode> items, String cursor) {
    public ReadResult {
      items = List.copyOf(items);
    }
  }
}
