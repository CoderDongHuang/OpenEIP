package com.openeip.connector.adapter.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookConnectorTest {
  private HttpServer server;
  private String endpoint;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
          } else {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(202, 0);
          }
          exchange.close();
        });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void validatesEndpointAndSendsSignedPayload() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var connector = new WebhookConnector(mapper);
    var values = mapper.createObjectNode().put("endpoint", endpoint).put("allowInsecure", true);
    var config =
        new com.openeip.connector.spi.ConnectorConfig(values, Map.of("signingSecret", "secret"));
    assertThat(connector.testConnection(config).success()).isTrue();
    assertThat(
            connector
                .createWriter(config)
                .orElseThrow()
                .write(
                    new com.openeip.connector.spi.DataWriter.WriteRequest(
                        "deliveries",
                        "SEND",
                        mapper.createObjectNode().put("event", "created"),
                        "key"))
                .status())
        .isEqualTo("SENT");
    assertThat(WebhookConnector.sign("secret", "value")).hasSize(64);
  }
}
