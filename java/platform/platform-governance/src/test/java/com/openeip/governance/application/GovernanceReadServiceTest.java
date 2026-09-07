package com.openeip.governance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.infrastructure.persistence.JdbcAuditOutboxAdapter;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class GovernanceReadServiceTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final Instant FIRST = Instant.parse("2026-09-01T00:00:00Z");
  private JdbcTemplate jdbc;
  private GovernanceReadService reads;
  private AuditService audits;

  @BeforeEach
  void setUp() throws Exception {
    var source = new JdbcDataSource();
    source.setURL(
        "jdbc:h2:mem:governance-reads-"
            + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(source);
    String migration =
        new ClassPathResource("db/migration/V2.7.0__init_governance_schema.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    try (var connection = source.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection,
          new ByteArrayResource(h2Compatible(migration).getBytes(StandardCharsets.UTF_8)));
    }
    createTenant(TENANT, "Tenant One", "tenant-one");
    createTenant(OTHER_TENANT, "Tenant Two", "tenant-two");
    jdbc.update(
        """
        INSERT INTO governance_memberships
          (id, tenant_id, principal_id, roles_json, state, policy_version,
           revision, created_at, updated_at)
        VALUES (?, ?, ?, '[\"VIEWER\"]', 'ACTIVE', 'policy-1', 0,
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID().toString(),
        TENANT.toString(),
        PRINCIPAL.toString());
    reads = new GovernanceReadService(jdbc, new ObjectMapper());
    audits = new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper()));
    bind(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void returnsOnlyBoundTenantReadModels() {
    append(FIRST, "tenant.created", TENANT);
    bind(OTHER_TENANT);
    append(FIRST, "tenant.created", OTHER_TENANT);
    insertTrace(TENANT, "0123456789abcdef", "visible");
    insertTrace(OTHER_TENANT, "0123456789abcdef", "hidden");
    bind(TENANT);

    assertThat(reads.currentTenant()).containsEntry("displayName", "Tenant One");
    assertThat(reads.memberships(200)).hasSize(1);
    assertThat(reads.audits("tenant.created", "tenant", "success", null, null, 200))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("resourceId", TENANT.toString()));
    assertThat(reads.traces("0123456789abcdef", 200))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("operation", "visible"));
  }

  @Test
  void verifiesAChainSegmentAgainstItsPredecessorAndDetectsTampering() {
    append(FIRST, "first", TENANT);
    append(FIRST.plusSeconds(1), "second", TENANT);
    append(FIRST.plusSeconds(2), "third", TENANT);

    assertThat(reads.verifyAudit(FIRST.plusSeconds(1), FIRST.plusSeconds(2)))
        .containsEntry("valid", true)
        .containsEntry("recordCount", 2);

    jdbc.update(
        "UPDATE governance_audit_records SET previous_hash = ? WHERE action = 'second'",
        "0".repeat(64));
    assertThat(reads.verifyAudit(FIRST.plusSeconds(1), FIRST.plusSeconds(2)))
        .containsEntry("valid", false);
  }

  @Test
  void rejectsUnboundedOrMalformedQueries() {
    assertThatThrownBy(() -> reads.verifyAudit(FIRST, FIRST.plusSeconds(32L * 24 * 60 * 60)))
        .isInstanceOf(GovernanceAuditException.class);
    assertThatThrownBy(() -> reads.verifyAudit(null, FIRST))
        .isInstanceOf(GovernanceAuditException.class);
    assertThatThrownBy(() -> reads.traces("not-a-trace", 10))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  private void createTenant(UUID tenantId, String name, String slug) {
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        tenantId.toString(),
        tenantId.toString(),
        name,
        slug);
  }

  private void append(Instant occurredAt, String action, UUID tenantId) {
    audits.append(
        AuditService.command(
            UUID.randomUUID(),
            tenantId,
            PRINCIPAL,
            action,
            "tenant",
            tenantId.toString(),
            AuditOutcome.SUCCESS,
            "request-1",
            "0123456789abcdef0123456789abcdef",
            "policy-1",
            occurredAt,
            Map.of()));
  }

  private void insertTrace(UUID tenantId, String traceId, String operation) {
    jdbc.update(
        """
        INSERT INTO governance_trace_links
          (id, tenant_id, trace_id, request_id, module, operation, outcome,
           safe_attributes_json, occurred_at)
        VALUES (?, ?, ?, 'request-1', 'governance', ?, 'SUCCESS', '{}', CURRENT_TIMESTAMP)
        """,
        UUID.randomUUID().toString(),
        tenantId.toString(),
        traceId,
        operation);
  }

  private static void bind(UUID tenantId) {
    TenantContextHolder.bind(
        new TenantContext(
            tenantId,
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("VIEWER"),
            "policy-1",
            "request-1",
            "0123456789abcdef0123456789abcdef",
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private static String h2Compatible(String mysql) {
    String transformed =
        mysql
            .replaceAll("(?m)^\\s*KEY [^\\r\\n]+,?\\r?\\n", "")
            .replaceAll("UNIQUE KEY ([A-Za-z0-9_]+) \\(", "CONSTRAINT $1 UNIQUE (")
            .replaceAll(
                "\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;", ");");
    return transformed.replaceAll(",\\s*\\);", "\\n);");
  }
}
