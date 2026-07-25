package com.openeip.connector.spi;

import java.util.List;

public record MetadataSchema(List<ResourceSchema> resources) {
  public MetadataSchema {
    resources = List.copyOf(resources);
  }

  public record ResourceSchema(String name, String kind, List<String> fields) {
    public ResourceSchema {
      fields = List.copyOf(fields);
    }
  }
}
