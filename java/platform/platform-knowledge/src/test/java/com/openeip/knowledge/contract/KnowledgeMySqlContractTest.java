package com.openeip.knowledge.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.domain.repository.DocumentFileRepository;
import com.openeip.knowledge.KnowledgeTestApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = KnowledgeTestApplication.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeMySqlContractTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.4@sha256:23818b7d7de427096ab1427b2e3d9d5e14a5b933f9a4431a482d6414bc879091")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_knowledge_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired DocumentFileRepository files;

  @Test
  @Order(1)
  void migrationCreatesFourTablesAndTenantLeadingIndexes() {
    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name IN ('knowledge_bases', 'knowledge_base_members',
                'knowledge_base_documents', 'knowledge_processed_events')
            """,
            Integer.class);
    Integer uniqueEvents =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
              AND table_name = 'knowledge_processed_events' AND index_name = 'uk_knowledge_event'
            """,
            Integer.class);
    assertThat(tables).isEqualTo(4);
    assertThat(uniqueEvents).isEqualTo(2);

    files.saveAndFlush(
        new DocumentFile(
            "33333333-3333-3333-3333-333333333333",
            "default",
            "11111111-1111-1111-1111-111111111111",
            "notes.txt",
            "aa/33333333-3333-3333-3333-333333333333",
            "text/plain",
            5,
            "a".repeat(64),
            Instant.parse("2026-07-22T00:00:00Z")));
    assertThat(files.count()).isEqualTo(1);
  }

  @Test
  @Order(2)
  void openApiAndEventContractsAreVersionedAndClosed() throws Exception {
    Path root = repositoryRoot();
    JsonNode paths =
        new ObjectMapper(new YAMLFactory())
            .readTree(root.resolve("docs/06-api/knowledge-base-v1.openapi.yaml").toFile())
            .path("paths");
    assertThat(paths.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                "/api/v1/knowledge/bases",
                "/api/v1/knowledge/bases/{baseId}",
                "/api/v1/knowledge/bases/{baseId}/documents",
                "/api/v1/knowledge/bases/{baseId}/documents/{documentId}",
                "/api/v1/knowledge/bases/{baseId}/documents/{documentId}/processing"));
    JsonNode event =
        new ObjectMapper()
            .readTree(
                root.resolve("contracts/events/embedding.job.completed.v1.schema.json").toFile());
    assertThat(event.path("additionalProperties").asBoolean()).isFalse();
    assertThat(event.path("properties").path("eventVersion").path("const").asInt()).isEqualTo(1);
    assertThat(event.path("properties").path("payload").path("additionalProperties").asBoolean())
        .isFalse();
  }

  @Test
  @Order(3)
  void rollbackRemovesOnlyKnowledgeTables() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.info().applied()).isNotEmpty();
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.2.0__init_knowledge_schema.sql"));
    }
    Integer knowledgeTables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name LIKE 'knowledge_%'
            """,
            Integer.class);
    Integer documentTable =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name = 'document_files'
            """,
            Integer.class);
    assertThat(knowledgeTables).isZero();
    assertThat(documentTable).isOne();
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
