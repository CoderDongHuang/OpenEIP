package com.openeip.document.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openeip.document.DocumentTestApplication;
import com.openeip.document.application.service.DocumentFileService;
import com.openeip.document.domain.entity.DocumentFile;
import com.openeip.document.domain.repository.DocumentFileRepository;
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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = DocumentTestApplication.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentMySqlContractTest {

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.4@sha256:23818b7d7de427096ab1427b2e3d9d5e14a5b933f9a4431a482d6414bc879091")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_document_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  @TempDir static Path storageRoot;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("openeip.document.storage-root", storageRoot::toString);
  }

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired DocumentFileRepository repository;

  @Test
  @Order(1)
  void migrationCreatesConstraintsIndexesAndOwnerQueries() {
    Integer tableCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'document_files'
            """,
            Integer.class);
    Integer indexCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'document_files'
            """,
            Integer.class);
    assertThat(tableCount).isEqualTo(1);
    assertThat(indexCount).isEqualTo(4);

    DocumentFile first = file("11111111-1111-1111-1111-111111111111", "aa/" + uuid(1));
    repository.saveAndFlush(first);
    assertThat(
            repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
                first.getId(), DocumentFileService.MVP_TENANT, "owner-1"))
        .isPresent();
    assertThat(
            repository.findByIdAndTenantIdAndOwnerIdAndDeletedAtIsNull(
                first.getId(), DocumentFileService.MVP_TENANT, "owner-2"))
        .isEmpty();

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    file("22222222-2222-2222-2222-222222222222", first.getObjectKey())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Order(2)
  void openApiAndEventContractsExposeExactlyTheDesignedSurface() throws Exception {
    Path root = findRepositoryRoot();
    JsonNode paths =
        new ObjectMapper(new YAMLFactory())
            .readTree(root.resolve("docs/06-api/file-upload-v1.openapi.yaml").toFile())
            .path("paths");
    Set<String> operations =
        Set.of(
            "/api/v1/documents/files#post",
            "/api/v1/documents/files#get",
            "/api/v1/documents/files/{fileId}#get",
            "/api/v1/documents/files/{fileId}#delete",
            "/api/v1/documents/files/{fileId}/content#get");
    for (String operation : operations) {
      String[] parts = operation.split("#");
      assertThat(paths.path(parts[0]).has(parts[1])).as(operation).isTrue();
    }
    assertThat(paths.size()).isEqualTo(3);

    JsonNode event =
        new ObjectMapper()
            .readTree(
                root.resolve("contracts/events/document.file.uploaded.v1.schema.json").toFile());
    assertThat(event.path("properties").path("eventType").path("const").asText())
        .isEqualTo("document.file.uploaded");
    assertThat(event.path("properties").path("eventVersion").path("const").asText())
        .isEqualTo("1.0");
    assertThat(event.path("additionalProperties").asBoolean()).isFalse();
    assertThat(event.path("required").toString()).contains("\"eventId\"");
  }

  @Test
  @Order(3)
  void rollbackRemovesDocumentTable() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.info().applied()).isNotEmpty();
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.1.0__init_document_file_schema.sql"));
      Integer count =
          jdbcTemplate.queryForObject(
              """
              SELECT COUNT(*) FROM information_schema.tables
              WHERE table_schema = DATABASE() AND table_name = 'document_files'
              """,
              Integer.class);
      assertThat(count).isZero();
    }
  }

  private static DocumentFile file(String id, String key) {
    return new DocumentFile(
        id,
        DocumentFileService.MVP_TENANT,
        "owner-1",
        "notes.txt",
        key,
        "text/plain",
        5,
        "a".repeat(64),
        Instant.parse("2026-07-22T00:00:00Z"));
  }

  private static String uuid(int suffix) {
    return "00000000-0000-0000-0000-" + String.format("%012d", suffix);
  }

  private static Path findRepositoryRoot() {
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
