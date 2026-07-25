package com.openeip.connector.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class FeishuConnector extends RestJsonConnectorSpi {
  private final HttpClient tokenClient;

  public FeishuConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.FEISHU, "Feishu", "1.0.0", "Feishu Open API connector", true, true),
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
    tokenClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField(
            "endpoint",
            "Feishu endpoint",
            ConfigField.FieldType.URL,
            false,
            false,
            "https://open.feishu.cn",
            List.of()),
        new ConfigField("appId", "App ID", ConfigField.FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "appSecret", "App secret", ConfigField.FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "allowInsecure",
            "Allow HTTP",
            ConfigField.FieldType.BOOLEAN,
            false,
            false,
            "false",
            List.of()));
  }

  @Override
  protected String defaultEndpoint() {
    return "https://open.feishu.cn";
  }

  @Override
  protected String testPath() {
    return "/open-apis/tenant/v2/tenant/query";
  }

  @Override
  protected String metadataPath() {
    return "/open-apis/drive/v1/files?page_size=100";
  }

  @Override
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    return switch (resource) {
      case "files" -> "/open-apis/drive/v1/files?page_size=100";
      case "wiki" -> "/open-apis/wiki/v2/spaces?page_size=100";
      default -> "/open-apis/tenant/v2/tenant/query";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"messages".equals(resource) || !"SEND".equalsIgnoreCase(operation)) {
      throw new com.openeip.connector.shared.ConnectorAdapterException(
          "CONN-FEISHU-WRITE", "Only message sending is allowed", false);
    }
    return "/open-apis/im/v1/messages?receive_id_type=open_id";
  }

  @Override
  protected Map<String, String> headers(ConnectorConfig config) {
    return Map.of(
        HttpHeaders.ACCEPT,
        "application/json",
        HttpHeaders.CONTENT_TYPE,
        "application/json",
        HttpHeaders.AUTHORIZATION,
        "Bearer " + VendorToken.feishu(tokenClient, mapper, config));
  }
}
