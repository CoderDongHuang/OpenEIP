package com.openeip.connector.adapter.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import org.springframework.stereotype.Component;

@Component
public class OracleConnector extends JdbcConnectorSpi {
  public OracleConnector(ObjectMapper mapper) {
    super(ConnectorType.ORACLE, "Oracle", "Oracle database connector", 1521, mapper);
  }

  @Override
  protected String jdbcUrl(JsonNode config) {
    return "jdbc:oracle:thin:@//"
        + required(config, "host")
        + ":"
        + port(config)
        + "/"
        + required(config, "database");
  }

  @Override
  protected String pingSql() {
    return "SELECT 1 FROM DUAL";
  }

  @Override
  protected String limitClause(int limit) {
    return " FETCH FIRST " + limit + " ROWS ONLY";
  }
}
