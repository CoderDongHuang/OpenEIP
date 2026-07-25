package com.openeip.connector.adapter.objectstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConnectorMetadata;
import org.springframework.stereotype.Component;

@Component
public class OssConnector extends S3CompatibleConnector {
  public OssConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.OSS,
            "OSS",
            "1.0.0",
            "Alibaba Cloud OSS S3-compatible connector",
            true,
            true),
        "https://oss-cn-hangzhou.aliyuncs.com",
        mapper);
  }
}
