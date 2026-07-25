package com.openeip.connector.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.shared.ConnectorAdapterException;
import com.openeip.connector.spi.ConfigField;
import com.openeip.connector.spi.ConfigField.FieldType;
import com.openeip.connector.spi.ConnectionTestResult;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.ConnectorMetadata;
import com.openeip.connector.spi.ConnectorSpi;
import com.openeip.connector.spi.DataReader;
import com.openeip.connector.spi.DataWriter;
import com.openeip.connector.spi.MetadataSchema;
import com.openeip.connector.spi.MetadataSchema.ResourceSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public abstract class RestJsonConnectorSpi implements ConnectorSpi {
  private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private final ConnectorMetadata metadata;
  protected final ObjectMapper mapper;
  private final HttpClient client;

  protected RestJsonConnectorSpi(
      ConnectorMetadata metadata, ObjectMapper mapper, HttpClient client) {
    this.metadata = metadata;
    this.mapper = mapper;
    this.client = client;
  }

  @Override
  public final ConnectorMetadata getMetadata() {
    return metadata;
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField(
            "endpoint", "Endpoint", FieldType.URL, false, false, defaultEndpoint(), List.of()),
        new ConfigField("username", "Username", FieldType.TEXT, false, true, null, List.of()),
        new ConfigField("password", "Password", FieldType.TEXT, false, true, null, List.of()),
        new ConfigField("token", "Access token", FieldType.TEXT, false, true, null, List.of()),
        new ConfigField(
            "allowInsecure", "Allow HTTP", FieldType.BOOLEAN, false, false, "false", List.of()));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try {
      JsonNode body = request(config, "GET", testPath(), null);
      if (body == null) {
        throw new ConnectorAdapterException("CONN-HTTP-EMPTY", "Empty response", true);
      }
      return ConnectionTestResult.success(elapsed(started));
    } catch (ConnectorAdapterException exception) {
      return ConnectionTestResult.failure(
          elapsed(started), exception.getCode(), exception.getMessage());
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try {
      JsonNode body = request(config, "GET", metadataPath(), null);
      List<ResourceSchema> resources = new ArrayList<>();
      JsonNode array =
          body.isArray() ? body : firstArray(body, "values", "items", "resources", "projects");
      if (array != null && array.isArray()) {
        for (JsonNode item : array) {
          if (resources.size() >= 200) {
            break;
          }
          List<String> fields = new ArrayList<>();
          item.fieldNames().forEachRemaining(fields::add);
          resources.add(
              new ResourceSchema(
                  item.path("id").asText(item.path("key").asText("resource")), "remote", fields));
        }
      } else {
        List<String> fields = new ArrayList<>();
        body.fieldNames().forEachRemaining(fields::add);
        resources.add(new ResourceSchema("root", "remote", fields));
      }
      return new MetadataSchema(resources);
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw adapter("CONN-HTTP-METADATA", "Remote metadata extraction failed", exception, true);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> {
      try {
        JsonNode body =
            request(config, "GET", readPath(config, request.resource(), request.query()), null);
        List<JsonNode> items = new ArrayList<>();
        JsonNode array =
            body.isArray()
                ? body
                : firstArray(body, "values", "items", "issues", "data", "content");
        if (array != null && array.isArray()) {
          array
              .elements()
              .forEachRemaining(
                  item -> {
                    if (items.size() < request.limit()) {
                      items.add(item);
                    }
                  });
        } else if (!body.isNull()) {
          items.add(body);
        }
        return new DataReader.ReadResult(items, null);
      } catch (ConnectorAdapterException exception) {
        throw exception;
      } catch (Exception exception) {
        throw adapter("CONN-HTTP-READ", "Remote read failed", exception, true);
      }
    };
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    if (!metadata.writable()) {
      return Optional.empty();
    }
    return Optional.of(
        request -> {
          try {
            JsonNode body =
                request(
                    config,
                    "POST",
                    writePath(config, request.resource(), request.operation()),
                    request.data());
            return new DataWriter.WriteResult(body.path("id").asText(request.resource()), "SENT");
          } catch (ConnectorAdapterException exception) {
            throw exception;
          } catch (Exception exception) {
            throw adapter("CONN-HTTP-WRITE", "Remote write failed", exception, true);
          }
        });
  }

  protected abstract String defaultEndpoint();

  protected abstract String testPath();

  protected abstract String metadataPath();

  protected abstract String readPath(ConnectorConfig config, String resource, JsonNode query);

  protected abstract String writePath(ConnectorConfig config, String resource, String operation);

  protected Map<String, String> headers(ConnectorConfig config) {
    return Map.of(
        HttpHeaders.ACCEPT, "application/json", HttpHeaders.CONTENT_TYPE, "application/json");
  }

  protected final JsonNode request(
      ConnectorConfig config, String method, String path, JsonNode body) {
    try {
      URI endpoint = endpoint(config);
      URI uri = endpoint.resolve(normalizedPath(resolvePath(config, path)));
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(20))
              .header(HttpHeaders.ACCEPT, "application/json");
      headers(config).forEach(builder::header);
      if ("GET".equals(method)) {
        builder.GET();
      } else {
        builder.method(
            method,
            HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(body == null ? mapper.createObjectNode() : body)));
      }
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
        throw new ConnectorAdapterException(
            "CONN-HTTP-SIZE", "Remote response is too large", false);
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        boolean retryable =
            response.statusCode() == 408
                || response.statusCode() == 429
                || response.statusCode() >= 500;
        String code =
            response.statusCode() == 401 || response.statusCode() == 403
                ? "CONN-AUTH"
                : "CONN-HTTP-" + response.statusCode();
        throw new ConnectorAdapterException(
            code,
            retryable
                ? "Remote service temporarily unavailable"
                : "Remote service rejected request",
            retryable);
      }
      if (response.body().isBlank()) {
        return mapper.createObjectNode();
      }
      return mapper.readTree(response.body());
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (java.net.http.HttpTimeoutException exception) {
      throw adapter("CONN-TIMEOUT", "Remote service timed out", exception, true);
    } catch (Exception exception) {
      throw adapter("CONN-HTTP-TRANSPORT", "Remote service is unavailable", exception, true);
    }
  }

  protected final URI endpoint(ConnectorConfig config) {
    String raw = config.values().path("endpoint").asText(defaultEndpoint()).trim();
    try {
      URI uri = URI.create(raw);
      if (!Set.of("https", "http").contains(uri.getScheme())
          || uri.getUserInfo() != null
          || uri.getHost() == null) {
        throw new IllegalArgumentException();
      }
      if ("http".equalsIgnoreCase(uri.getScheme())
          && !config.values().path("allowInsecure").asBoolean(false)) {
        throw new ConnectorAdapterException(
            "CONN-TLS", "HTTP endpoints require allowInsecure=true", false);
      }
      return uri;
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ConnectorAdapterException("CONN-ENDPOINT", "Invalid connector endpoint", false);
    }
  }

  protected static String basic(ConnectorConfig config) {
    String username = config.credentials().get("username");
    String password = config.credentials().get("password");
    if (username == null || password == null) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "Basic credentials are incomplete", false);
    }
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  protected static String bearer(ConnectorConfig config) {
    String token = config.credentials().get("token");
    if (token == null || token.isBlank()) {
      throw new ConnectorAdapterException("CONN-CREDENTIAL", "Access token is missing", false);
    }
    return "Bearer " + token;
  }

  protected static String configValue(ConnectorConfig config, String name) {
    return config.values().path(name).asText("").trim();
  }

  protected String resolvePath(ConnectorConfig config, String path) {
    return path;
  }

  protected static String queryValue(JsonNode query, String name) {
    return query == null ? "" : query.path(name).asText("").trim();
  }

  protected static String encode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String normalizedPath(String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }

  private static JsonNode firstArray(JsonNode node, String... names) {
    for (String name : names) {
      if (node.has(name) && node.get(name).isArray()) {
        return node.get(name);
      }
    }
    return null;
  }

  private static long elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private static ConnectorAdapterException adapter(
      String code, String message, Exception cause, boolean retryable) {
    ConnectorAdapterException result = new ConnectorAdapterException(code, message, retryable);
    result.initCause(cause);
    return result;
  }
}
