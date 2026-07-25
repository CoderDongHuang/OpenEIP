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
public class GitLabConnector extends RestJsonConnectorSpi {
  public GitLabConnector(ObjectMapper mapper) {
    super(
        new ConnectorMetadata(
            ConnectorType.GITLAB, "GitLab", "1.0.0", "GitLab REST connector", true, true),
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
            "project",
            "Project ID or path",
            ConfigField.FieldType.TEXT,
            false,
            false,
            null,
            List.of()));
    return fields;
  }

  @Override
  protected String defaultEndpoint() {
    return "https://gitlab.com/api/v4";
  }

  @Override
  protected String testPath() {
    return "/user";
  }

  @Override
  protected String metadataPath() {
    return "/projects?membership=true&per_page=100";
  }

  @Override
  protected String readPath(ConnectorConfig config, String resource, JsonNode query) {
    String project = project(configValue(config, "project"));
    return switch (resource) {
      case "projects" -> "/projects?membership=true&per_page=100";
      case "issues" -> "/projects/" + encode(project) + "/issues?per_page=100";
      case "files" -> "/projects/" + encode(project) + "/repository/tree?per_page=100";
      default -> "/user";
    };
  }

  @Override
  protected String writePath(ConnectorConfig config, String resource, String operation) {
    if (!"issues".equals(resource) || !"CREATE".equalsIgnoreCase(operation)) {
      throw new ConnectorAdapterException(
          "CONN-GITLAB-WRITE", "Only issue creation is allowed", false);
    }
    return "/projects/" + encode(project(configValue(config, "project"))) + "/issues";
  }

  @Override
  protected Map<String, String> headers(ConnectorConfig config) {
    String token = config.credentials().get("token");
    if (token == null || token.isBlank()) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "GitLab access token is missing", false);
    }
    return Map.of(
        HttpHeaders.ACCEPT,
        "application/json",
        HttpHeaders.CONTENT_TYPE,
        "application/json",
        "PRIVATE-TOKEN",
        token);
  }

  private static String project(String value) {
    if (!value.matches("[A-Za-z0-9_.:/-]{1,200}")) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid GitLab project", false);
    }
    return value;
  }
}
