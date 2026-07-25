package com.openeip.connector.adapter.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import org.springframework.stereotype.Component;

@Component
public class PostgresqlConnector extends JdbcConnectorSpi {
  public PostgresqlConnector(ObjectMapper mapper) {
    super(ConnectorType.POSTGRESQL, "PostgreSQL", "PostgreSQL database connector", 5432, mapper);
  }

  @Override
  protected String jdbcUrl(JsonNode config) {
    return "jdbc:postgresql://"
        + required(config, "host")
        + ":"
        + port(config)
        + "/"
        + required(config, "database")
        + "?sslmode="
        + (bool(config, "ssl", true) ? "require" : "disable");
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
