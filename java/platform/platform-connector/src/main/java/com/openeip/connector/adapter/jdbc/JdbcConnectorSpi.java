package com.openeip.connector.adapter.jdbc;

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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

abstract class JdbcConnectorSpi implements ConnectorSpi {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,127}");
  private final ConnectorMetadata metadata;
  private final int defaultPort;
  private final ObjectMapper mapper;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "The mapper is an application-scoped collaborator.")
  JdbcConnectorSpi(
      ConnectorType type, String name, String description, int defaultPort, ObjectMapper mapper) {
    this.metadata = new ConnectorMetadata(type, name, "1.0.0", description, true, true);
    this.defaultPort = defaultPort;
    this.mapper = mapper;
  }

  @Override
  public ConnectorMetadata getMetadata() {
    return metadata;
  }

  @Override
  public List<ConfigField> getConfigSchema() {
    return List.of(
        field("host", "Host", FieldType.TEXT, true, false, null),
        field("port", "Port", FieldType.NUMBER, true, false, Integer.toString(defaultPort)),
        field("database", "Database", FieldType.TEXT, true, false, null),
        field("schema", "Schema", FieldType.TEXT, false, false, null),
        field("ssl", "TLS", FieldType.BOOLEAN, false, false, "true"),
        field("username", "Username", FieldType.TEXT, true, true, null),
        field("password", "Password", FieldType.TEXT, true, true, null));
  }

  @Override
  public ConnectionTestResult testConnection(ConnectorConfig config) {
    long started = System.nanoTime();
    try (Connection connection = connect(config);
        PreparedStatement statement = connection.prepareStatement(pingSql())) {
      statement.setQueryTimeout(10);
      statement.execute();
      return ConnectionTestResult.success(elapsed(started));
    } catch (Exception exception) {
      return ConnectionTestResult.failure(
          elapsed(started), "CONN-JDBC-CONNECTION", "Database connection failed");
    }
  }

  @Override
  public MetadataSchema extractMetadata(ConnectorConfig config) {
    try (Connection connection = connect(config)) {
      DatabaseMetaData database = connection.getMetaData();
      String schema = optional(config.values(), "schema");
      List<ResourceSchema> resources = new ArrayList<>();
      try (ResultSet tables =
          database.getTables(null, schema, "%", new String[] {"TABLE", "VIEW"})) {
        while (tables.next() && resources.size() < 200) {
          String tableSchema = tables.getString("TABLE_SCHEM");
          String table = tables.getString("TABLE_NAME");
          List<String> fields = new ArrayList<>();
          try (ResultSet columns = database.getColumns(null, tableSchema, table, "%")) {
            while (columns.next() && fields.size() < 500) {
              fields.add(columns.getString("COLUMN_NAME"));
            }
          }
          String resource =
              tableSchema == null || tableSchema.isBlank() ? table : tableSchema + "." + table;
          resources.add(new ResourceSchema(resource, tables.getString("TABLE_TYPE"), fields));
        }
      }
      return new MetadataSchema(resources);
    } catch (Exception exception) {
      throw adapter("CONN-JDBC-METADATA", "Database metadata extraction failed", exception);
    }
  }

  @Override
  public DataReader createReader(ConnectorConfig config) {
    return request -> read(config, request);
  }

  @Override
  public Optional<DataWriter> createWriter(ConnectorConfig config) {
    return Optional.of(request -> write(config, request));
  }

  protected abstract String jdbcUrl(JsonNode config);

  protected abstract String pingSql();

  protected abstract String limitClause(int limit);

  protected final int port(JsonNode config) {
    int value = config.path("port").asInt(defaultPort);
    if (value < 1 || value > 65535) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Invalid database port", false);
    }
    return value;
  }

  protected static String required(JsonNode config, String name) {
    String value = config.path(name).asText("").trim();
    if (value.isEmpty()) {
      throw new ConnectorAdapterException("CONN-CONFIG", "Missing configuration: " + name, false);
    }
    return value;
  }

  protected static boolean bool(JsonNode config, String name, boolean fallback) {
    return config.has(name) ? config.path(name).asBoolean(fallback) : fallback;
  }

  private DataReader.ReadResult read(ConnectorConfig config, DataReader.ReadRequest request) {
    try (Connection connection = connect(config)) {
      String table = qualified(connection, request.resource());
      String sql = "SELECT * FROM " + table + limitClause(request.limit());
      List<JsonNode> items = new ArrayList<>();
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setQueryTimeout(30);
        statement.setMaxRows(request.limit());
        try (ResultSet rows = statement.executeQuery()) {
          ResultSetMetaData columns = rows.getMetaData();
          while (rows.next() && items.size() < request.limit()) {
            ObjectNode item = mapper.createObjectNode();
            for (int index = 1; index <= columns.getColumnCount(); index++) {
              Object value = rows.getObject(index);
              item.set(
                  columns.getColumnLabel(index),
                  value == null ? mapper.nullNode() : mapper.valueToTree(value));
            }
            items.add(item);
          }
        }
      }
      return new DataReader.ReadResult(items, null);
    } catch (Exception exception) {
      throw adapter("CONN-JDBC-READ", "Database read failed", exception);
    }
  }

  @SuppressFBWarnings(
      value = "SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING",
      justification =
          "SQL structure uses identifiers validated by IDENTIFIER; values remain bound parameters.")
  private DataWriter.WriteResult write(ConnectorConfig config, DataWriter.WriteRequest request) {
    if (!"INSERT".equalsIgnoreCase(request.operation())
        || !request.data().isObject()
        || request.data().isEmpty()) {
      throw new ConnectorAdapterException(
          "CONN-JDBC-WRITE", "Only non-empty INSERT operations are allowed", false);
    }
    try (Connection connection = connect(config)) {
      String table = qualified(connection, request.resource());
      List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
      request.data().fields().forEachRemaining(fields::add);
      if (fields.size() > 100) {
        throw new ConnectorAdapterException("CONN-JDBC-WRITE", "Too many insert fields", false);
      }
      String quote = quote(connection);
      String columns =
          fields.stream()
              .map(field -> quoted(field.getKey(), quote))
              .reduce((a, b) -> a + "," + b)
              .orElseThrow();
      String placeholders = String.join(",", java.util.Collections.nCopies(fields.size(), "?"));
      String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
      try (PreparedStatement statement =
          connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        statement.setQueryTimeout(30);
        for (int index = 0; index < fields.size(); index++) {
          statement.setObject(index + 1, scalar(fields.get(index).getValue()));
        }
        int changed = statement.executeUpdate();
        try (ResultSet keys = statement.getGeneratedKeys()) {
          String id = keys.next() ? String.valueOf(keys.getObject(1)) : Integer.toString(changed);
          return new DataWriter.WriteResult(id, "INSERTED");
        }
      }
    } catch (ConnectorAdapterException exception) {
      throw exception;
    } catch (Exception exception) {
      throw adapter("CONN-JDBC-WRITE", "Database write failed", exception);
    }
  }

  private Connection connect(ConnectorConfig config) throws Exception {
    String username = credential(config, "username");
    String password = credential(config, "password");
    return DriverManager.getConnection(jdbcUrl(config.values()), username, password);
  }

  private static String credential(ConnectorConfig config, String name) {
    String value = config.credentials().get(name);
    if (value == null) {
      throw new ConnectorAdapterException(
          "CONN-CREDENTIAL", "Missing database credential: " + name, false);
    }
    return value;
  }

  private static String qualified(Connection connection, String resource) throws Exception {
    String quote = quote(connection);
    String[] parts = resource.split("\\.", -1);
    if (parts.length < 1 || parts.length > 2) {
      throw new ConnectorAdapterException("CONN-RESOURCE", "Invalid database resource", false);
    }
    return java.util.Arrays.stream(parts)
        .map(part -> quoted(part, quote))
        .reduce((a, b) -> a + "." + b)
        .orElseThrow();
  }

  private static String quote(Connection connection) throws Exception {
    String value = connection.getMetaData().getIdentifierQuoteString();
    return value.trim();
  }

  private static String quoted(String value, String quote) {
    if (!IDENTIFIER.matcher(value).matches()) {
      throw new ConnectorAdapterException("CONN-RESOURCE", "Invalid database identifier", false);
    }
    return quote + value + quote;
  }

  private static Object scalar(JsonNode value) {
    if (value.isNull()) {
      return null;
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isIntegralNumber()) {
      return value.longValue();
    }
    if (value.isFloatingPointNumber()) {
      return value.decimalValue();
    }
    if (value.isTextual()) {
      return value.textValue();
    }
    throw new ConnectorAdapterException("CONN-JDBC-WRITE", "Insert values must be scalar", false);
  }

  private static String optional(JsonNode config, String name) {
    String value = config.path(name).asText("").trim();
    return value.isEmpty() ? null : value;
  }

  private static ConfigField field(
      String name,
      String label,
      FieldType type,
      boolean required,
      boolean secret,
      String defaultValue) {
    return new ConfigField(name, label, type, required, secret, defaultValue, List.of());
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
