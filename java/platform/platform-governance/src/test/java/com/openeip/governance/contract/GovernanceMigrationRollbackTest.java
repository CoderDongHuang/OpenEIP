package com.openeip.governance.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class GovernanceMigrationRollbackTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";

  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>(
              DockerImageName.parse(
                      "mysql:8.4.10@sha256:5700b0892591a760c4caef7a0024c887afd46317d73dd420801706e661c4db56")
                  .asCompatibleSubstituteFor("mysql"))
          .withDatabaseName("openeip_governance_contract")
          .withUsername("openeip")
          .withPassword("contract-password");

  @Test
  void migrationEnforcesTenantForeignKeysAndRollbackRemovesAllTables() throws Exception {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection =
        DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
      assertThat(governanceTableCount(connection)).isEqualTo(20);
      insertTenants(connection);
      insertOrganization(connection);
      assertThatThrownBy(() -> insertCrossTenantMembership(connection))
          .isInstanceOf(Exception.class);
      insertQuotaPolicy(connection);
      assertThatThrownBy(() -> insertCrossTenantQuotaReservation(connection))
          .isInstanceOf(Exception.class);
      insertAuditRecords(connection);

      assertThat(auditEventCount(connection, TENANT_ONE)).isEqualTo(1);
      assertThat(auditEventCount(connection, TENANT_TWO)).isEqualTo(1);

      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/rollback/U2.7.0__init_governance_schema.sql"));
      assertThat(governanceTableCount(connection)).isZero();
    }
  }

  private static void insertTenants(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """)) {
      statement.setString(1, TENANT_ONE);
      statement.setString(2, TENANT_ONE);
      statement.setString(3, "Tenant One");
      statement.setString(4, "tenant-one");
      statement.executeUpdate();
      statement.setString(1, TENANT_TWO);
      statement.setString(2, TENANT_TWO);
      statement.setString(3, "Tenant Two");
      statement.setString(4, "tenant-two");
      statement.executeUpdate();
    }
  }

  private static void insertOrganization(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_organizations
          (id, tenant_id, name, created_at, updated_at)
        VALUES (?, ?, 'Operations', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """)) {
      statement.setString(1, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
      statement.setString(2, TENANT_ONE);
      statement.executeUpdate();
    }
  }

  private static void insertCrossTenantMembership(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_memberships
          (id, tenant_id, organization_id, principal_id, roles_json, state, policy_version,
           created_at, updated_at)
        VALUES (?, ?, ?, ?, '["ROLE_OPERATOR"]', 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """)) {
      statement.setString(1, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
      statement.setString(2, TENANT_TWO);
      statement.setString(3, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
      statement.setString(4, "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
      statement.executeUpdate();
    }
  }

  private static void insertQuotaPolicy(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_quota_policies
          (id, tenant_id, name, policy_version, token_limit, window_type, created_at, updated_at)
        VALUES (?, ?, 'runtime-quota', 'policy-1', 100, 'DAILY',
                CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """)) {
      statement.setString(1, "77777777-7777-4777-8777-777777777777");
      statement.setString(2, TENANT_ONE);
      statement.executeUpdate();
    }
  }

  private static void insertCrossTenantQuotaReservation(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_quota_reservations
          (id, tenant_id, quota_policy_id, execution_id, idempotency_key, policy_version,
           window_type, window_start, window_end, requested_token_units,
           requested_cost_amount, requested_request_units, requested_concurrency_units,
           observed_token_units, observed_cost_amount, observed_request_units,
           observed_concurrency_units, decision, request_id, trace_id, expires_at, created_at)
        VALUES (?, ?, ?, ?, 'quota-migration-test', 'policy-1', 'DAILY',
                '2026-09-05 00:00:00', '2026-09-06 00:00:00', 1, 0, 1, 1,
                0, 0, 0, 0, 'ALLOW', 'request-1',
                '0123456789abcdef0123456789abcdef',
                '2026-09-05 13:00:00', '2026-09-05 12:00:00')
        """)) {
      statement.setString(1, "88888888-8888-4888-8888-888888888888");
      statement.setString(2, TENANT_TWO);
      statement.setString(3, "77777777-7777-4777-8777-777777777777");
      statement.setString(4, "99999999-9999-4999-8999-999999999999");
      statement.executeUpdate();
    }
  }

  private static void insertAuditRecords(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
        INSERT INTO governance_audit_records
          (id, tenant_id, event_id, principal_id, action, resource_type, resource_id, outcome,
           request_id, trace_id, policy_version, schema_version, occurred_at, record_hash, summary_json)
        VALUES (?, ?, ?, ?, 'tenant.created', 'tenant', ?, 'SUCCESS', 'request-1',
                '0123456789abcdef0123456789abcdef', 'policy-1', 'governance.event.v1',
                CURRENT_TIMESTAMP(6), ?, '{}')
        """)) {
      insertAudit(
          statement,
          "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
          TENANT_ONE,
          "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
          "hash-one");
      insertAudit(
          statement,
          "ffffffff-ffff-4fff-8fff-ffffffffffff",
          TENANT_TWO,
          "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
          "hash-two");
      assertThatThrownBy(
              () ->
                  insertAudit(
                      statement,
                      "99999999-9999-4999-8999-999999999999",
                      TENANT_ONE,
                      "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
                      "different-hash"))
          .isInstanceOf(Exception.class);
    }
  }

  private static void insertAudit(
      java.sql.PreparedStatement statement, String id, String tenantId, String eventId, String hash)
      throws Exception {
    statement.setString(1, id);
    statement.setString(2, tenantId);
    statement.setString(3, eventId);
    statement.setString(4, "cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    statement.setString(5, id);
    statement.setString(6, hash);
    statement.executeUpdate();
  }

  private static int governanceTableCount(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'governance_%'
                """)) {
      result.next();
      return result.getInt(1);
    }
  }

  private static int auditEventCount(Connection connection, String tenantId) throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM governance_audit_records WHERE tenant_id = ?")) {
      statement.setString(1, tenantId);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }
}
