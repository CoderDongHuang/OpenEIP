package com.openeip.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.openeip.connector.domain.ConnectorType;
import com.openeip.connector.spi.ConfigField.FieldType;
import com.openeip.connector.spi.DataReader.ReadRequest;
import com.openeip.connector.spi.DataReader.ReadResult;
import com.openeip.connector.spi.DataWriter.WriteRequest;
import com.openeip.connector.spi.DataWriter.WriteResult;
import com.openeip.connector.spi.MetadataSchema.ResourceSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorSpiValueTest {
  @Test
  void valueContractsAreImmutableAndExposeSpiData() throws Exception {
    ConnectorMetadata metadata =
        new ConnectorMetadata(ConnectorType.MYSQL, "MySQL", "1.0", "database", true, true);
    assertThat(metadata.type()).isEqualTo(ConnectorType.MYSQL);
    assertThat(metadata.name()).isEqualTo("MySQL");
    assertThat(metadata.version()).isEqualTo("1.0");
    assertThat(metadata.description()).isEqualTo("database");
    assertThat(metadata.readable()).isTrue();
    assertThat(metadata.writable()).isTrue();

    ConfigField field =
        new ConfigField("host", "Host", FieldType.TEXT, true, false, "localhost", null);
    assertThat(field.name()).isEqualTo("host");
    assertThat(field.label()).isEqualTo("Host");
    assertThat(field.type()).isEqualTo(FieldType.TEXT);
    assertThat(field.required()).isTrue();
    assertThat(field.secret()).isFalse();
    assertThat(field.defaultValue()).isEqualTo("localhost");
    assertThat(field.options()).isEmpty();
    assertThat(FieldType.values())
        .contains(FieldType.NUMBER, FieldType.BOOLEAN, FieldType.SELECT, FieldType.URL);

    var values =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            new ObjectMapper().readTree("{\"host\":\"db\"}");
    Map<String, String> mutableCredentials = new HashMap<>();
    mutableCredentials.put("username", "user");
    ConnectorConfig config = new ConnectorConfig(values, mutableCredentials);
    mutableCredentials.clear();
    values.put("host", "changed");
    assertThat(config.credentials()).containsEntry("username", "user");
    assertThat(config.values().path("host").asText()).isEqualTo("db");

    List<String> mutableFields = new ArrayList<>(List.of("id"));
    ResourceSchema resource = new ResourceSchema("users", "table", mutableFields);
    MetadataSchema schema = new MetadataSchema(new ArrayList<>(List.of(resource)));
    mutableFields.clear();
    assertThat(schema.resources()).hasSize(1);
    assertThat(resource.name()).isEqualTo("users");
    assertThat(resource.kind()).isEqualTo("table");
    assertThat(resource.fields()).containsExactly("id");

    ReadRequest read = new ReadRequest("users", TextNode.valueOf("all"), 10);
    ReadResult readResult =
        new ReadResult(new ArrayList<>(List.of(TextNode.valueOf("item"))), "next");
    assertThat(read.resource()).isEqualTo("users");
    assertThat(read.query().asText()).isEqualTo("all");
    assertThat(read.limit()).isEqualTo(10);
    assertThat(readResult.items()).hasSize(1);
    assertThat(readResult.cursor()).isEqualTo("next");

    WriteRequest write = new WriteRequest("users", "create", TextNode.valueOf("data"), "key");
    WriteResult writeResult = new WriteResult("42", "created");
    assertThat(write.resource()).isEqualTo("users");
    assertThat(write.operation()).isEqualTo("create");
    assertThat(write.data().asText()).isEqualTo("data");
    assertThat(write.idempotencyKey()).isEqualTo("key");
    assertThat(writeResult.resourceId()).isEqualTo("42");
    assertThat(writeResult.status()).isEqualTo("created");

    assertThat(ConnectionTestResult.success(5))
        .isEqualTo(new ConnectionTestResult(true, 5, "OK", "Connection succeeded"));
    assertThat(ConnectionTestResult.failure(6, "TIMEOUT", "slow").success()).isFalse();
  }
}
