package com.openeip.connector.adapter.objectstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S3CompatibleConnectorTest {
  private HttpServer server;
  private String endpoint;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] body =
              "<ListBucketResult><Contents><Key>one.txt</Key><Size>1</Size></Contents></ListBucketResult>"
                  .getBytes(StandardCharsets.UTF_8);
          if ("HEAD".equals(exchange.getRequestMethod())) {
            body = new byte[0];
          }
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
  void signsAndExecutesBucketOperationsAgainstFixture() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    var connector = new MinioConnector(mapper);
    var values =
        mapper
            .createObjectNode()
            .put("endpoint", endpoint)
            .put("allowInsecure", true)
            .put("bucket", "demo-bucket");
    var config =
        new com.openeip.connector.spi.ConnectorConfig(
            values, Map.of("accessKey", "access", "secretKey", "secret"));
    assertThat(connector.testConnection(config).success()).isTrue();
    assertThat(connector.extractMetadata(config).resources()).isNotEmpty();
    assertThat(
            connector
                .createReader(config)
                .read(
                    new com.openeip.connector.spi.DataReader.ReadRequest(
                        "objects", mapper.createObjectNode(), 10))
                .items())
        .hasSize(1);
    assertThat(
            connector
                .createWriter(config)
                .orElseThrow()
                .write(
                    new com.openeip.connector.spi.DataWriter.WriteRequest(
                        "one.txt", "PUT", mapper.createObjectNode().put("value", "x"), "key"))
                .status())
        .isEqualTo("STORED");
  }
}
