package com.openeip.connector.adapter.objectstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConnectorMetadata;
import org.springframework.stereotype.Component;

@Component
public class MinioConnector extends S3CompatibleConnector {
  public MinioConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.MINIO, "MinIO", "1.0.0", "MinIO S3 connector", true, true),
        "https://minio.example.com",
        mapper);
  }
}
