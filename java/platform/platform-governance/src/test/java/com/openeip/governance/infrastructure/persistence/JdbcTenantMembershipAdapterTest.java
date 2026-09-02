package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcTenantMembershipAdapterTest {
  private static final String TENANT_ID = "11111111-1111-4111-8111-111111111111";
  private static final UUID PRINCIPAL_ID = UUID.randomUUID();
  private JdbcTemplate jdbc;
  private JdbcTenantMembershipAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    String migration =
        new ClassPathResource("db/migration/V2.7.0__init_governance_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
    adapter = new JdbcTenantMembershipAdapter(jdbc, new ObjectMapper());
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, 'Tenant One', 'tenant-one', 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        TENANT_ID,
        TENANT_ID);
  }

  @Test
  void readsOnlyOneActiveServerMembership() {
    UUID membershipId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO governance_memberships
          (id, tenant_id, principal_id, roles_json, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, '["ROLE_OPERATOR"]', 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        membershipId.toString(),
        TENANT_ID,
        PRINCIPAL_ID.toString());

    var result = adapter.findActiveByPrincipal(PRINCIPAL_ID);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().membershipId()).isEqualTo(membershipId);
    assertThat(result.orElseThrow().tenantId()).isEqualTo(UUID.fromString(TENANT_ID));
    assertThat(result.orElseThrow().roles()).containsExactly("ROLE_OPERATOR");
  }

  @Test
  void rejectsAmbiguousActiveMembershipsFailClosed() {
    String secondTenant = "22222222-2222-4222-8222-222222222222";
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, 'Tenant Two', 'tenant-two', 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        secondTenant,
        secondTenant);
    insertMembership(TENANT_ID);
    insertMembership(secondTenant);

    assertThat(adapter.findActiveByPrincipal(PRINCIPAL_ID)).isEmpty();
  }

  private void insertMembership(String tenantId) {
    jdbc.update(
        """
        INSERT INTO governance_memberships
          (id, tenant_id, principal_id, roles_json, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, '[\"ROLE_OPERATOR\"]', 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID().toString(),
        tenantId,
        PRINCIPAL_ID.toString());
  }

  private static String h2Compatible(String mysql) {
    String transformed =
        mysql
            .replaceAll("(?m)^\\s*KEY [^\\r\\n]+,?\\r?\\n", "")
            .replaceAll("UNIQUE KEY ([A-Za-z0-9_]+) \\(", "CONSTRAINT $1 UNIQUE (")
            .replaceAll(
                "\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", ");");
    return transformed.replaceAll(",\\s*\\);", "\n);");
  }
}
