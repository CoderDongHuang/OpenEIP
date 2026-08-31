package com.openeip.agent.v2.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentMySqlContractTest {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.10@sha256:5700b0892591a760c4caef7a0024c887afd46317d73dd420801706e661c4db56")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_agent_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
  }

  @Test
  @Order(1)
  void migrationCreatesAllAgentTablesAndDurabilityIndexes() {
    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND
              (table_name LIKE 'agent_%'
               OR table_name LIKE 'tool_%'
               OR table_name LIKE 'mcp_%'
               OR table_name LIKE 'eval_%')
            """,
            Integer.class);
    Integer durableIndexes =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND index_name IN
              ('uk_agent_version_number', 'uk_agent_attempt_idempotency',
               'uk_agent_command_idempotency', 'uk_agent_run_event_sequence',
               'uk_agent_outbox_event', 'uk_agent_memory_purge',
               'uk_mcp_tool_mapping', 'uk_eval_case_result')
            """,
            Integer.class);
    assertThat(tables).isEqualTo(31);
    assertThat(durableIndexes).isEqualTo(8);
  }

  @Test
  @Order(2)
  void candidateColumnsPermitPromotionWithoutConsumingVersionNumbers() {
    String versionNumberNullable =
        jdbc.queryForObject(
            """
            SELECT is_nullable FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'agent_version'
              AND column_name = 'version_number'
            """,
            String.class);
    Integer lifecycleColumns =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'agent_version'
              AND column_name IN
                ('snapshot_status', 'source_draft_revision', 'published_at',
                 'published_by', 'evaluation_run_id')
            """,
            Integer.class);
    assertThat(versionNumberNullable).isEqualTo("YES");
    assertThat(lifecycleColumns).isEqualTo(5);
  }

  @Test
  @Order(3)
  void rollbackRemovesOnlyAgentPlatformTables() throws Exception {
    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.6.0__init_agent_platform_schema.sql"));
    }
    Integer tables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND
              (table_name LIKE 'agent_%'
               OR table_name LIKE 'tool_%'
               OR table_name LIKE 'mcp_%'
               OR table_name LIKE 'eval_%')
            """,
            Integer.class);
    Integer priorModuleTables =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'auth_users'
            """,
            Integer.class);
    assertThat(tables).isZero();
    assertThat(priorModuleTables).isOne();
  }
}
