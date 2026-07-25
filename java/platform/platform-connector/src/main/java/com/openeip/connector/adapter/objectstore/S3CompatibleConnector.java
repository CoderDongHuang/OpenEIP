package com.openeip.connector.adapter.objectstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

abstract class S3CompatibleConnector implements ConnectorSpi {
  private static final DateTimeFormatter AMZ_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SHORT_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
  private final ConnectorMetadata metadata;
  private final String defaultEndpoint;
  protected final ObjectMapper mapper;
  private final HttpClient client;

  protected S3CompatibleConnector(
      ConnectorMetadata metadata, String defaultEndpoint, ObjectMapper mapper) {
    this.metadata = metadata;
    this.defaultEndpoint = defaultEndpoint;
    this.mapper = mapper;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return metadata;
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField(
            "endpoint", "S3 endpoint", FieldType.URL, false, false, defaultEndpoint, List.of()),
        new ConfigField("bucket", "Bucket", FieldType.TEXT, true, false, null, List.of()),
        new ConfigField("region", "Region", FieldType.TEXT, false, false, "us-east-1", List.of()),
        new ConfigField("accessKey", "Access key", FieldType.TEXT, true, true, null, List.of()),
        new ConfigField("secretKey", "Secret key", FieldType.TEXT, true, true, null, List.of()),
        new ConfigField(
            "allowInsecure", "Allow HTTP", FieldType.BOOLEAN, false, false, "false", List.of()));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try {
      send(config, "HEAD", "", null);
      return ConnectionTestResult.success(elapsed(started));
    } catch (ConnectorAdapterException exception) {
      return ConnectionTestResult.failure(
          elapsed(started), exception.getCode(), exception.getMessage());
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try {
      JsonNode result = send(config, "GET", "?list-type=2&max-keys=100", null);
      List<String> fields = new ArrayList<>();
      result.fieldNames().forEachRemaining(fields::add);
      return new MetadataSchema(List.of(new ResourceSchema("objects", "bucket", fields)));
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw adapter("CONN-OBJECT-METADATA", "Object metadata extraction failed", exception, true);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> {
      String object = request.resource();
      try {
        JsonNode result =
            send(
                config,
                "GET",
                "objects".equals(object)
                    ? "?list-type=2&max-keys=" + request.limit()
                    : "/" + path(object),
                null);
        if ("objects".equals(object)) {
          return new DataReader.ReadResult(parseList(result, request.limit()), null);
        }
        return new DataReader.ReadResult(List.of(result), null);
      } catch (ConnectorAdapterException exception) {
        throw exception;
      } catch (Exception exception) {
        throw adapter("CONN-OBJECT-READ", "Object read failed", exception, true);
      }
    };
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(
        request -> {
          String object = path(request.resource());
          try {
            if ("DELETE".equalsIgnoreCase(request.operation())) {
              send(config, "DELETE", "/" + object, null);
              return new DataWriter.WriteResult(object, "DELETED");
            }
            if (!"PUT".equalsIgnoreCase(request.operation())) {
              throw new ConnectorAdapterException(
                  "CONN-OBJECT-WRITE", "Only PUT or DELETE is allowed", false);
            }
            String payload = mapper.writeValueAsString(request.data());
            send(config, "PUT", "/" + object, payload.getBytes(StandardCharsets.UTF_8));
            return new DataWriter.WriteResult(object, "STORED");
          } catch (ConnectorAdapterException exception) {
            throw exception;
          } catch (Exception exception) {
            throw adapter("CONN-OBJECT-WRITE", "Object write failed", exception, true);
          }
        });
  }

  private JsonNode send(ConnectorConfig config, String method, String suffix, byte[] payload) {
    try {
      URI endpoint = endpoint(config);
      String normalized =
          suffix.startsWith("?") ? suffix : suffix.startsWith("/") ? suffix : "/" + suffix;
      URI uri =
          endpoint.resolve(
              endpoint.getPath().endsWith("/")
                  ? endpoint.getPath().substring(0, endpoint.getPath().length() - 1) + normalized
                  : endpoint.getPath() + normalized);
      byte[] body = payload == null ? new byte[0] : payload;
      Instant now = Instant.now();
      String amzDate = AMZ_DATE.format(now);
      String shortDate = SHORT_DATE.format(now);
      String region = config.values().path("region").asText("us-east-1");
      String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
      String hash = hex(sha256(body));
      String canonicalUri = uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
      String canonicalQuery = uri.getRawQuery() == null ? "" : uri.getRawQuery();
      String canonicalHeaders =
          "host:"
              + host
              + "\n"
              + "x-amz-content-sha256:"
              + hash
              + "\n"
              + "x-amz-date:"
              + amzDate
              + "\n";
      String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
      String canonicalRequest =
          method
              + "\n"
              + canonicalUri
              + "\n"
              + canonicalQuery
              + "\n"
              + canonicalHeaders
              + "\n"
              + signedHeaders
              + "\n"
              + hash;
      String scope = shortDate + "/" + region + "/s3/aws4_request";
      String stringToSign =
          "AWS4-HMAC-SHA256\n"
              + amzDate
              + "\n"
              + scope
              + "\n"
              + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
      byte[] signingKey =
          hmac(
              hmac(
                  hmac(
                      hmac(("AWS4" + secret(config)).getBytes(StandardCharsets.UTF_8), shortDate),
                      region),
                  "s3"),
              "aws4_request");
      String signature = hex(hmac(signingKey, stringToSign));
      String authorization =
          "AWS4-HMAC-SHA256 Credential="
              + access(config)
              + "/"
              + scope
              + ", SignedHeaders="
              + signedHeaders
              + ", Signature="
              + signature;
      HttpRequest.Builder request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(30))
              .header("x-amz-date", amzDate)
              .header("x-amz-content-sha256", hash)
              .header("Authorization", authorization);
      request.method(
          method,
          body.length == 0
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(body));
      HttpResponse<byte[]> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ConnectorAdapterException(
            "CONN-OBJECT-" + response.statusCode(),
            "Object storage request failed",
            response.statusCode() >= 500 || response.statusCode() == 429);
      }
      if (response.body().length == 0) {
        return mapper.createObjectNode();
      }
      String text = new String(response.body(), StandardCharsets.UTF_8);
      if (text.trim().startsWith("<")) {
        return parseXml(text);
      }
      try {
        return mapper.readTree(text);
      } catch (Exception ignored) {
        return mapper
            .createObjectNode()
            .put("content", java.util.Base64.getEncoder().encodeToString(response.body()));
      }
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw adapter("CONN-OBJECT-TRANSPORT", "Object storage is unavailable", exception, true);
    }
  }

  private URI endpoint(ConnectorConfig config) {
    String raw = config.values().path("endpoint").asText(defaultEndpoint).trim();
    URI uri;
    try {
      uri = URI.create(raw);
    } catch (Exception exception) {
      throw new ConnectorAdapterException("CONN-ENDPOINT", "Invalid object endpoint", false);
    }
    if (!List.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null) {
      throw new ConnectorAdapterException("CONN-ENDPOINT", "Invalid object endpoint", false);
    }
    if ("http".equalsIgnoreCase(uri.getScheme())
        && !config.values().path("allowInsecure").asBoolean(false)) {
      throw new ConnectorAdapterException(
          "CONN-TLS", "HTTP endpoints require allowInsecure=true", false);
    }
    return URI.create(uri.toString().replaceAll("/$", "") + "/" + bucket(config));
  }

  private static String bucket(ConnectorConfig config) {
    String value = config.values().path("bucket").asText("");
    if (!value.matches("[A-Za-z0-9][A-Za-z0-9.-]{1,62}")) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid bucket", false);
    }
    return value;
  }

  private static String path(String value) {
    if (value == null
        || value.isBlank()
        || value.length() > 1024
        || value.startsWith("/")
        || value.contains("..")) {
      throw new ConnectorAdapterException("CONN-RESOURCE", "Invalid object key", false);
    }
    return value;
  }

  private static String access(ConnectorConfig config) {
    String value = config.credentials().get("accessKey");
    if (value == null || value.isBlank()) {
      throw new ConnectorAdapterException("CONN-CREDENTIAL", "Missing access key", false);
    }
    return value;
  }

  private static String secret(ConnectorConfig config) {
    String value = config.credentials().get("secretKey");
    if (value == null || value.isBlank()) {
      throw new ConnectorAdapterException("CONN-CREDENTIAL", "Missing secret key", false);
    }
    return value;
  }

  private List<JsonNode> parseList(JsonNode result, int limit) {
    List<JsonNode> items = new ArrayList<>();
    JsonNode values = result.path("contents");
    if (values.isArray()) {
      values
          .elements()
          .forEachRemaining(
              value -> {
                if (items.size() < limit) {
                  items.add(value);
                }
              });
    }
    return items;
  }

  private JsonNode parseXml(String text) throws Exception {
    ObjectNode result = mapper.createObjectNode();
    var contents = mapper.createArrayNode();
    XMLStreamReader reader =
        XMLInputFactory.newFactory().createXMLStreamReader(new java.io.StringReader(text));
    String current = null;
    ObjectNode item = null;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        current = reader.getLocalName();
        if ("Contents".equals(current)) {
          item = mapper.createObjectNode();
        }
      } else if (event == XMLStreamConstants.CHARACTERS
          && current != null
          && !reader.isWhiteSpace()) {
        if (item != null) {
          item.put(current, reader.getText());
        } else {
          result.put(current, reader.getText());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        if ("Contents".equals(reader.getLocalName()) && item != null) {
          contents.add(item);
          item = null;
        }
        current = null;
      }
    }
    reader.close();
    if (!contents.isEmpty()) {
      result.set("contents", contents);
    }
    return result;
  }

  private static byte[] sha256(byte[] value) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(value);
  }

  private static byte[] hmac(byte[] key, String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(byte[] value) {
    return java.util.HexFormat.of().formatHex(value);
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
