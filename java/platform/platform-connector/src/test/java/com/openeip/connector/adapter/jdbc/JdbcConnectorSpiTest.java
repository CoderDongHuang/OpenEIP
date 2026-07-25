package com.openeip.connector.adapter.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConnectorConfig;
import com.openeip.connector.spi.DataReader;
import com.openeip.connector.spi.DataWriter;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcConnectorSpiTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final H2Connector connector = new H2Connector(mapper);
  private ConnectorConfig config;

  @BeforeEach
  void setUp() throws Exception {
    config =
        new ConnectorConfig(
            mapper.readTree(
                "{\"host\":\"localhost\",\"port\":9092,\"database\":\"test\",\"schema\":\"PUBLIC\",\"ssl\":false}"),
            Map.of("username", "sa", "password", "password"));
    try (var connection =
        DriverManager.getConnection(
            "jdbc:h2:mem:connector;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=password")) {
      try (var statement = connection.createStatement()) {
        statement.execute(
            "CREATE TABLE IF NOT EXISTS TEST_DATA (ID INT AUTO_INCREMENT PRIMARY KEY, NAME VARCHAR(100))");
        statement.execute("DELETE FROM TEST_DATA");
      }
    }
  }

  @Test
  void testsConnectionMetadataReadAndParameterizedInsert() {
    assertThat(connector.testConnection(config).success()).isTrue();
    assertThat(connector.extractMetadata(config).resources()).isNotEmpty();
    DataWriter writer = connector.createWriter(config).orElseThrow();
    var inserted =
        writer.write(
            new DataWriter.WriteRequest(
                "TEST_DATA", "INSERT", mapper.createObjectNode().put("NAME", "Alice"), "write-1"));
    assertThat(inserted.status()).isEqualTo("INSERTED");
    DataReader.ReadResult read =
        connector
            .createReader(config)
            .read(new DataReader.ReadRequest("TEST_DATA", mapper.createObjectNode(), 10));
    assertThat(read.items()).anyMatch(item -> "Alice".equals(item.path("NAME").asText()));
  }

  private static final class H2Connector extends JdbcConnectorSpi {
    H2Connector(ObjectMapper mapper) {
      super(ConnectorType.MYSQL, "H2", "test", 9092, mapper);
    }

    @Override
    protected String jdbcUrl(com.fasterxml.jackson.databind.JsonNode config) {
      return "jdbc:h2:mem:connector;DB_CLOSE_DELAY=-1";
    }

    @Override
    protected String pingSql() {
      return "SELECT 1";
    }

    @Override
    protected String limitClause(int limit) {
      return " LIMIT " + limit;
    }
  }
}
