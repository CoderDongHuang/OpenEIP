package com.openeip.connector.adapter.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RedisConnector implements ConnectorSpi {
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper is an application-scoped collaborator.")
  public RedisConnector(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return new ConnectorMetadata(
        ConnectorType.REDIS, "Redis", "1.0.0", "Redis key-value connector", true, true);
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        new ConfigField("host", "Host", FieldType.TEXT, true, false, null, List.of()),
        new ConfigField("port", "Port", FieldType.NUMBER, true, false, "6379", List.of()),
        new ConfigField("database", "Database", FieldType.NUMBER, false, false, "0", List.of()),
        new ConfigField("ssl", "TLS", FieldType.BOOLEAN, false, false, "true", List.of()),
        new ConfigField("username", "Username", FieldType.TEXT, false, true, null, List.of()),
        new ConfigField("password", "Password", FieldType.TEXT, false, true, null, List.of()));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try (RedisClient client = client(config);
        StatefulRedisConnection<String, String> connection = client.connect()) {
      connection.sync().ping();
      return ConnectionTestResult.success(elapsed(started));
    } catch (Exception exception) {
      return ConnectionTestResult.failure(
          elapsed(started), "CONN-REDIS-CONNECTION", "Redis connection failed");
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try (RedisClient client = client(config);
        StatefulRedisConnection<String, String> connection = client.connect()) {
      String info = connection.sync().info("server");
      List<String> fields =
          info.lines()
              .filter(line -> line.contains(":"))
              .map(line -> line.substring(0, line.indexOf(':')))
              .limit(100)
              .toList();
      return new MetadataSchema(List.of(new ResourceSchema("keys", "redis", fields)));
    } catch (Exception exception) {
      throw adapter("CONN-REDIS-METADATA", "Redis metadata extraction failed", exception);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> {
      try (RedisClient client = client(config);
          StatefulRedisConnection<String, String> connection = client.connect()) {
        RedisCommands<String, String> commands = connection.sync();
        if ("keys".equalsIgnoreCase(request.resource())) {
          String pattern =
              request.query() == null ? "*" : request.query().path("pattern").asText("*");
          List<JsonNode> items = new ArrayList<>();
          ScanCursor cursor = ScanCursor.INITIAL;
          do {
            var scan =
                commands.scan(cursor, ScanArgs.Builder.matches(pattern).limit(request.limit()));
            for (String key : scan.getKeys()) {
              ObjectNode item = mapper.createObjectNode();
              item.put("key", key);
              item.put("value", commands.get(key));
              items.add(item);
              if (items.size() >= request.limit()) {
                break;
              }
            }
            cursor = scan;
          } while (!cursor.isFinished() && items.size() < request.limit());
          return new DataReader.ReadResult(items, cursor.isFinished() ? null : cursor.getCursor());
        }
        String key = key(request.resource());
        String value = commands.get(key);
        return new DataReader.ReadResult(
            value == null
                ? List.of()
                : List.of(mapper.createObjectNode().put("key", key).put("value", value)),
            null);
      } catch (Exception exception) {
        throw adapter("CONN-REDIS-READ", "Redis read failed", exception);
      }
    };
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(
        request -> {
          if (!request.data().isObject()) {
            throw new ConnectorAdapterException(
                "CONN-REDIS-WRITE", "Redis data must be an object", false);
          }
          String key = key(request.data().path("key").asText(""));
          try (RedisClient client = client(config);
              StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            if ("DELETE".equalsIgnoreCase(request.operation())) {
              commands.del(key);
              return new DataWriter.WriteResult(key, "DELETED");
            }
            if (!"SET".equalsIgnoreCase(request.operation())) {
              throw new ConnectorAdapterException(
                  "CONN-REDIS-WRITE", "Only SET or DELETE is allowed", false);
            }
            String value = request.data().path("value").asText("");
            long ttl = request.data().path("ttlSeconds").asLong(0);
            if (ttl > 0) {
              commands.setex(key, ttl, value);
            } else {
              commands.set(key, value);
            }
            return new DataWriter.WriteResult(key, "STORED");
          } catch (ConnectorAdapterException exception) {
            throw exception;
          } catch (Exception exception) {
            throw adapter("CONN-REDIS-WRITE", "Redis write failed", exception);
          }
        });
  }

  private RedisClient client(ConnectorConfig config) {
    String host = required(config.values(), "host");
    int port = config.values().path("port").asInt(6379);
    if (port < 1 || port > 65535) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid Redis port", false);
    }
    RedisURI.Builder builder =
        RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withDatabase(config.values().path("database").asInt(0));
    if (config.values().path("ssl").asBoolean(true)) {
      builder.withSsl(true);
    }
    String username = config.credentials().get("username");
    String password = config.credentials().get("password");
    if (password != null) {
      builder.withAuthentication(username == null ? "default" : username, password);
    }
    RedisClient client = RedisClient.create(builder.build());
    client.setDefaultTimeout(Duration.ofSeconds(10));
    return client;
  }

  private static String required(JsonNode config, String field) {
    String value = config.path(field).asText("").trim();
    if (value.isBlank()) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Missing Redis config: " + field, false);
    }
    return value;
  }

  private static String key(String value) {
    if (value.isBlank() || value.length() > 512 || value.indexOf('\n') >= 0) {
      throw new ConnectorAdapterException("CONN-RESOURCE", "Invalid Redis key", false);
    }
    return value;
  }

  private static long elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }

  private static ConnectorAdapterException adapter(String code, String message, Exception cause) {
    ConnectorAdapterException result = new ConnectorAdapterException(code, message, true);
    result.initCause(cause);
    return result;
  }
}
