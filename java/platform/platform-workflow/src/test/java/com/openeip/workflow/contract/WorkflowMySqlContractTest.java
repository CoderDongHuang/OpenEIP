package com.openeip.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openeip.workflow.WorkflowTestApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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

@SpringBootTest(classes = WorkflowTestApplication.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowMySqlContractTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.10@sha256:5700b0892591a760c4caef7a0024c887afd46317d73dd420801706e661c4db56")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_workflow_contract")
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
    registry.add("openeip.workflow.scheduler-enabled", () -> "false");
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  @Order(1)
  void migrationCreatesAllWorkflowTablesAndDurabilityIndexes() {
    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name LIKE 'workflow_%'
            """,
            Integer.class);
    Integer durableIndexes =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND index_name IN
              ('uk_workflow_execution_idempotency', 'uk_workflow_event_sequence',
               'uk_workflow_outbox_event', 'uk_workflow_processed_event')
            """,
            Integer.class);
    assertThat(tables).isEqualTo(11);
    assertThat(durableIndexes).isEqualTo(4);
  }

  @Test
  @Order(2)
  void openApiContainsDefinitionExecutionTriggerApprovalAndEventSurfaces() throws Exception {
    Path root = repositoryRoot();
    JsonNode paths =
        new ObjectMapper(new YAMLFactory())
            .readTree(root.resolve("docs/06-api/workflow-v1.openapi.yaml").toFile())
            .path("paths");
    assertThat(paths.fieldNames())
        .toIterable()
        .contains(
            "/workflows",
            "/workflows/{workflowId}/publish",
            "/workflows/{workflowId}/executions",
            "/workflow-executions/{executionId}/events:stream",
            "/workflow-approvals/{approvalId}/decisions",
            "/workflow-hooks/{hookId}");
    assertThat(paths.path("/workflows").fieldNames()).toIterable().contains("get", "post");

    ObjectMapper mapper = new ObjectMapper();
    JsonNode outbound =
        mapper.readTree(
            root.resolve("contracts/events/workflow.execution.event.v1.schema.json").toFile());
    JsonNode inbound =
        mapper.readTree(
            root.resolve("contracts/events/workflow.trigger.event.v1.schema.json").toFile());
    JsonNode dlq =
        mapper.readTree(
            root.resolve("contracts/events/workflow.event.rejected.v1.schema.json").toFile());
    assertThat(outbound.path("additionalProperties").asBoolean()).isFalse();
    assertThat(outbound.path("properties").path("eventVersion").path("const").asInt()).isEqualTo(1);
    assertThat(inbound.path("additionalProperties").asBoolean()).isFalse();
    assertThat(inbound.path("required")).anyMatch(value -> value.asText().equals("timestamp"));
    assertThat(dlq.path("properties").path("errorCode").path("const").asText())
        .isEqualTo("WF-V-001");
  }

  @Test
  @Order(3)
  void rollbackRemovesOnlyWorkflowTables() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load();
    assertThat(flyway.info().applied()).isNotEmpty();
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.4.0__init_workflow_schema.sql"));
    }
    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name LIKE 'workflow_%'
            """,
            Integer.class);
    assertThat(tables).isZero();
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
