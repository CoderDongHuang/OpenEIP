package com.openeip.connector.adapter.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.connector.domain.ConnectorType;
import org.springframework.stereotype.Component;

@Component
public class SapHanaConnector extends JdbcConnectorSpi {
  public SapHanaConnector(ObjectMapper mapper) {
    super(ConnectorType.SAP, "SAP HANA", "SAP HANA database connector", 30015, mapper);
  }

  @Override
  protected String jdbcUrl(JsonNode config) {
    return "jdbc:sap://"
        + required(config, "host")
        + ":"
        + port(config)
        + "/?databaseName="
        + required(config, "database")
        + "&encrypt="
        + bool(config, "ssl", true);
  }

  @Override
  protected String pingSql() {
    return "SELECT 1 FROM DUMMY";
  }

  @Override
  protected String limitClause(int limit) {
    return " LIMIT " + limit;
  }
}
