package com.openeip.connector.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestJsonConnectorSpiTest {
  private HttpServer server;
  private String endpoint;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body = "[{\"id\":\"repo-1\",\"name\":\"demo\"}]".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void executesGithubTestMetadataReadAndWriteAgainstProtocolFixture() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var connector = new GitHubConnector(mapper);
    var values =
        mapper
            .createObjectNode()
            .put("endpoint", endpoint)
            .put("allowInsecure", true)
            .put("repository", "owner/repo");
    var config = new com.openeip.connector.spi.ConnectorConfig(values, Map.of("token", "token"));
    assertThat(connector.testConnection(config).success()).isTrue();
    assertThat(connector.extractMetadata(config).resources()).isNotEmpty();
    assertThat(
            connector
                .createReader(config)
                .read(
                    new com.openeip.connector.spi.DataReader.ReadRequest(
                        "repositories", mapper.createObjectNode(), 10))
                .items())
        .hasSize(1);
    assertThat(
            connector
                .createWriter(config)
                .orElseThrow()
                .write(
                    new com.openeip.connector.spi.DataWriter.WriteRequest(
                        "issues", "CREATE", mapper.createObjectNode().put("title", "test"), "key"))
                .status())
        .isEqualTo("SENT");
  }
}
