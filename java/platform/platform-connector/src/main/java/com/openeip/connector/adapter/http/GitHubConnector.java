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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GitHubConnector extends RestJsonConnectorSpi {
  public GitHubConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.GITHUB, "GitHub", "1.0.0", "GitHub REST connector", true, true),
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    List<ConfigField> fields = new ArrayList<>(super.getConfigSchema());
    fields.add(
        new ConfigField(
            "repository",
            "Repository owner/name",
            ConfigField.FieldType.TEXT,
            false,
            false,
            null,
            List.of()));
    return fields;
  }

  @Override
  protected String defaultEndpoint() {
    return "https://api.github.com";
  }

  @Override
  protected String testPath() {
    return "/user";
  }

  @Override
  protected String metadataPath() {
    return "/user/repos?per_page=100";
  }

  @Override
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    String repository = configValue(config, "repository");
    return switch (resource) {
      case "repositories" -> "/user/repos?per_page=100";
      case "issues" -> "/repos/" + requiredRepository(repository) + "/issues?per_page=100";
      case "files" ->
          "/repos/"
              + requiredRepository(repository)
              + "/contents/"
              + encode(queryValue(query, "path"));
      default -> "/user";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"issues".equals(resource) || !"CREATE".equalsIgnoreCase(operation)) {
      throw new ConnectorAdapterException(
          "CONN-GITHUB-WRITE", "Only issue creation is allowed", false);
    }
    return "/repos/" + requiredRepository(configValue(config, "repository")) + "/issues";
  }

  @Override
  protected Map<String, String> headers(ConnectorConfig config) {
    return Map.of(
        HttpHeaders.ACCEPT,
        "application/vnd.github+json",
        HttpHeaders.CONTENT_TYPE,
        "application/json",
        HttpHeaders.AUTHORIZATION,
        bearer(config),
        "X-GitHub-Api-Version",
        "2022-11-28");
  }

  private static String requiredRepository(String value) {
    if (!value.matches("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")) {
      throw new ConnectorAdapterException(
          "CONN-CONFIG", "GitHub repository must be owner/name", false);
    }
    return value;
  }
}
