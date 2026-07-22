package com.openeip.chat.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatMySqlContractTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.4@sha256:23818b7d7de427096ab1427b2e3d9d5e14a5b933f9a4431a482d6414bc879091")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_chat_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  @Order(1)
  void migrationCreatesSessionMessageForeignKeysAndOwnerIndexes() throws Exception {
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      assertThat(count(connection, "chat_sessions")).isOne();
      assertThat(count(connection, "chat_messages")).isOne();
      assertThat(indexCount(connection, "chat_sessions", "idx_chat_session_owner_updated"))
          .isEqualTo(3);
      assertThat(indexCount(connection, "chat_messages", "uk_chat_message_order")).isEqualTo(3);
    }
  }

  @Test
  @Order(2)
  void sourceOpenApiContainsOnlyVersionedChatOperations() throws Exception {
    JsonNode paths =
        new ObjectMapper(new YAMLFactory())
            .readTree(repositoryRoot().resolve("docs/06-api/chat-v1.openapi.yaml").toFile())
            .path("paths");
    assertThat(paths.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                "/api/v1/chat/sessions",
                "/api/v1/chat/sessions/{sessionId}/messages",
                "/api/v1/chat/sessions/{sessionId}/messages:stream",
                "/api/v1/internal/chat/messages:stream"));
  }

  @Test
  @Order(3)
  void rollbackRemovesOnlyChatTables() throws Exception {
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.3.0__init_chat_schema.sql"));
      assertThat(count(connection, "chat_sessions")).isZero();
      assertThat(count(connection, "chat_messages")).isZero();
      assertThat(count(connection, "knowledge_bases")).isOne();
      assertThat(count(connection, "auth_users")).isOne();
    }
  }

  private static int count(Connection connection, String table) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_name = ?")) {
      statement.setString(1, table);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private static int indexCount(Connection connection, String table, String index)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.statistics"
                + " WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
      statement.setString(1, table);
      statement.setString(2, index);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null && !Files.isDirectory(current.resolve("docs/06-api"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("Unable to locate repository root");
    }
    return current;
  }
}
