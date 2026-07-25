package com.openeip.connector.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.shared.ConnectorAdapterException;
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
public class ConfluenceConnector extends RestJsonConnectorSpi {
  public ConfluenceConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.CONFLUENCE,
            "Confluence",
            "1.0.0",
            "Confluence REST connector",
            true,
            true),
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  @Override
  protected String defaultEndpoint() {
    return "https://confluence.example.com";
  }

  @Override
  protected String testPath() {
    return "/wiki/rest/api/user/current";
  }

  @Override
  protected String metadataPath() {
    return "/wiki/rest/api/space?limit=100";
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField(
            "endpoint",
            "Confluence endpoint",
            ConfigField.FieldType.URL,
            true,
            false,
            null,
            List.of()),
        new ConfigField(
            "username", "Account email", ConfigField.FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "password", "API token", ConfigField.FieldType.TEXT, true, true, null, List.of()),
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
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    return switch (resource) {
      case "spaces" -> "/wiki/rest/api/space?limit=100";
      case "content" ->
          "/wiki/rest/api/content/search?limit=100&cql=" + encode(queryValue(query, "cql"));
      default -> "/wiki/rest/api/user/current";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"content".equals(resource)
        || !("CREATE".equalsIgnoreCase(operation) || "UPDATE".equalsIgnoreCase(operation))) {
      throw new ConnectorAdapterException(
          "CONN-CONFLUENCE-WRITE", "Only content create/update is allowed", false);
    }
    return "UPDATE".equalsIgnoreCase(operation)
        ? "/wiki/rest/api/content/" + encode(configValue(config, "pageId"))
        : "/wiki/rest/api/content";
  }

  @Override
  protected Map<String, String> headers(ConnectorConfig config) {
    return Map.of(
        HttpHeaders.ACCEPT,
        "application/json",
        HttpHeaders.CONTENT_TYPE,
        "application/json",
        HttpHeaders.AUTHORIZATION,
        basic(config));
  }
}
