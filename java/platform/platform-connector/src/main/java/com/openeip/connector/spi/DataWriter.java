package com.openeip.connector.spi;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface DataWriter {
  WriteResult write(WriteRequest request);

  record WriteRequest(String resource, String operation, JsonNode data, String idempotencyKey) {}

  record WriteResult(String resourceId, String status) {}
}
