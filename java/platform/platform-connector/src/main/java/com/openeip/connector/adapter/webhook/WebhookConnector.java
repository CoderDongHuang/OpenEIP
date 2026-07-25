package com.openeip.connector.adapter.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookConnector implements ConnectorSpi {
  private final ObjectMapper mapper;
  private final HttpClient client;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper is an application-scoped collaborator.")
  public WebhookConnector(ObjectMapper mapper) {
    this.mapper = mapper;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return new ConnectorMetadata(
        ConnectorType.WEBHOOK, "Webhook", "1.0.0", "Signed HTTP webhook connector", true, true);
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField(
            "endpoint", "Webhook endpoint", FieldType.URL, true, false, null, List.of()),
        new ConfigField(
            "allowInsecure", "Allow HTTP", FieldType.BOOLEAN, false, false, "false", List.of()),
        new ConfigField(
            "signatureHeader",
            "Signature header",
            FieldType.TEXT,
            false,
            false,
            "X-OpenEIP-Signature",
            List.of()),
        new ConfigField(
            "signingSecret", "Signing secret", FieldType.TEXT, true, true, null, List.of()));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try {
      URI uri = endpoint(config);
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(10))
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 400) {
        throw new ConnectorAdapterException(
            "CONN-WEBHOOK-" + response.statusCode(),
            "Webhook endpoint rejected test",
            response.statusCode() >= 500 || response.statusCode() == 429);
      }
      return ConnectionTestResult.success(elapsed(started));
    } catch (ConnectorAdapterException exception) {
      return ConnectionTestResult.failure(
          elapsed(started), exception.getCode(), exception.getMessage());
    } catch (Exception exception) {
      return ConnectionTestResult.failure(
          elapsed(started), "CONN-WEBHOOK-CONNECTION", "Webhook endpoint unavailable");
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    return new MetadataSchema(
        List.of(
            new ResourceSchema(
                "deliveries",
                "webhook",
                List.of("eventId", "receivedAt", "payload", "signatureValid"))));
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> new DataReader.ReadResult(List.of(), null);
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(
        request -> {
          if (!"SEND".equalsIgnoreCase(request.operation())) {
            throw new ConnectorAdapterException(
                "CONN-WEBHOOK-WRITE", "Only SEND is allowed", false);
          }
          try {
            String payload = mapper.writeValueAsString(request.data());
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String signature = sign(secret(config), timestamp + "." + payload);
            HttpRequest http =
                HttpRequest.newBuilder(endpoint(config))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-OpenEIP-Timestamp", timestamp)
                    .header(
                        config.values().path("signatureHeader").asText("X-OpenEIP-Signature"),
                        "v1=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response =
                client.send(http, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
              throw new ConnectorAdapterException(
                  "CONN-WEBHOOK-" + response.statusCode(),
                  "Webhook delivery failed",
                  response.statusCode() >= 500 || response.statusCode() == 429);
            }
            return new DataWriter.WriteResult(request.idempotencyKey(), "SENT");
          } catch (ConnectorAdapterException exception) {
            throw exception;
          } catch (Exception exception) {
            throw new ConnectorAdapterException(
                "CONN-WEBHOOK-TRANSPORT", "Webhook delivery failed", true);
          }
        });
  }

  private URI endpoint(ConnectorConfig config) {
    try {
      URI uri = URI.create(config.values().path("endpoint").asText(""));
      if (uri.getHost() == null || !List.of("http", "https").contains(uri.getScheme())) {
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
      throw new ConnectorAdapterException("CONN-ENDPOINT", "Invalid webhook endpoint", false);
    }
  }

  private static String secret(ConnectorConfig config) {
    String value = config.credentials().get("signingSecret");
    if (value == null || value.isBlank()) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "Missing webhook signing secret", false);
    }
    return value;
  }

  public static String sign(String secret, String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("HMAC unavailable", exception);
    }
  }

  private static long elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }
}
