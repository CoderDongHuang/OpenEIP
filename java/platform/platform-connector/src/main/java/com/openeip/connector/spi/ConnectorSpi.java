package com.openeip.connector.spi;

import java.util.List;
import java.util.Optional;

public interface ConnectorSpi {
  ConnectorMetadata getMetadata();

  List<ConfigField> getConfigSchema();

  ConnectionTestResult testConnection(ConnectorConfig config);

  MetadataSchema extractMetadata(ConnectorConfig config);

  DataReader createReader(ConnectorConfig config);

  Optional<DataWriter> createWriter(ConnectorConfig config);
}
