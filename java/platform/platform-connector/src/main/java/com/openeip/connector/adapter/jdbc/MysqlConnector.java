package com.openeip.connector.adapter.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import org.springframework.stereotype.Component;

@Component
public class MysqlConnector extends JdbcConnectorSpi {
  public MysqlConnector(ObjectMapper mapper) {
    super(ConnectorType.MYSQL, "MySQL", "MySQL database connector", 3306, mapper);
  }

  @Override
  protected String jdbcUrl(JsonNode config) {
    return "jdbc:mysql://"
        + required(config, "host")
        + ":"
        + port(config)
        + "/"
        + required(config, "database")
        + "?useSSL="
        + bool(config, "ssl", true)
        + "&allowPublicKeyRetrieval=false";
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
