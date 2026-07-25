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
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class JiraConnector extends RestJsonConnectorSpi {
  public JiraConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.JIRA, "Jira", "1.0.0", "Jira REST connector", true, true),
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  @Override
  protected String defaultEndpoint() {
    return "https://jira.example.com";
  }

  @Override
  protected String testPath() {
    return "/rest/api/3/myself";
  }

  @Override
  protected String metadataPath() {
    return "/rest/api/3/project/search?maxResults=100";
  }

  @Override
  public java.util.List<ConfigField> getConfigSchema() {
    return java.util.List.of(
        new ConfigField(
            "endpoint",
            "Jira endpoint",
            ConfigField.FieldType.URL,
            true,
            false,
            null,
            java.util.List.of()),
        new ConfigField(
            "username",
            "Account email",
            ConfigField.FieldType.TEXT,
            true,
            true,
            null,
            java.util.List.of()),
        new ConfigField(
            "password",
            "API token",
            ConfigField.FieldType.TEXT,
            true,
            true,
            null,
            java.util.List.of()),
        new ConfigField(
            "allowInsecure",
            "Allow HTTP",
            ConfigField.FieldType.BOOLEAN,
            false,
            false,
            "false",
            java.util.List.of()));
  }

  @Override
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    return switch (resource) {
      case "projects" -> "/rest/api/3/project/search?maxResults=100";
      case "issues" -> "/rest/api/3/search?maxResults=100&jql=" + encode(queryValue(query, "jql"));
      default -> "/rest/api/3/myself";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"issues".equals(resource) || !"CREATE".equalsIgnoreCase(operation)) {
      throw new ConnectorAdapterException(
          "CONN-JIRA-WRITE", "Only issue creation is allowed", false);
    }
    return "/rest/api/3/issue";
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
