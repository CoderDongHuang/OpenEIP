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
public class WecomConnector extends RestJsonConnectorSpi {
  private final HttpClient tokenClient;

  public WecomConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.WECOM, "WeCom", "1.0.0", "WeCom API connector", true, true),
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
            "WeCom endpoint",
            ConfigField.FieldType.URL,
            false,
            false,
            "https://qyapi.weixin.qq.com",
            List.of()),
        new ConfigField(
            "corpId", "Corp ID", ConfigField.FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "corpSecret", "Corp secret", ConfigField.FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "agentId", "Agent ID", ConfigField.FieldType.TEXT, false, false, null, List.of()),
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
    return "https://qyapi.weixin.qq.com";
  }

  @Override
  protected String testPath() {
    return "/cgi-bin/get_api_domain";
  }

  @Override
  protected String metadataPath() {
    return "/cgi-bin/department/list";
  }

  @Override
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    return switch (resource) {
      case "departments" -> "/cgi-bin/department/list";
      case "users" ->
          "/cgi-bin/user/list?department_id=" + encode(queryValue(query, "departmentId"));
      default -> "/cgi-bin/get_api_domain";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"messages".equals(resource) || !"SEND".equalsIgnoreCase(operation)) {
      throw new com.openeip.connector.shared.ConnectorAdapterException(
          "CONN-WECOM-WRITE", "Only message sending is allowed", false);
    }
    return "/cgi-bin/message/send";
  }

  @Override
  protected Map<String, String> headers(ConnectorConfig config) {
    return Map.of(
        HttpHeaders.ACCEPT, "application/json", HttpHeaders.CONTENT_TYPE, "application/json");
  }

  @Override
  protected String resolvePath(ConnectorConfig config, String path) {
    String separator = path.contains("?") ? "&" : "?";
    return path
        + separator
        + "access_token="
        + encode(VendorToken.wecom(tokenClient, mapper, config));
  }
}
