package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.domain.audit.AuditOutcome;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.shared.exception.GovernanceAuditException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcAuditOutboxAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private JdbcTemplate jdbc;
  private DataSource dataSource;
  private JdbcAuditOutboxAdapter adapter;
  private AuditService service;
  private Instant occurredAt;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    this.dataSource = dataSource;
    dataSource.setURL(
        "jdbc:h2:mem:governance-audit-"
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
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        TENANT_ONE,
        TENANT_ONE,
        "Tenant One",
        "tenant-one");
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        TENANT_TWO,
        TENANT_TWO,
        "Tenant Two",
        "tenant-two");
    adapter = new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper());
    service = new AuditService(adapter);
    occurredAt = Instant.parse("2026-09-02T00:00:00Z");
    bind(TENANT_ONE);
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void appendsAuditAndOutboxTogetherWithSanitizedPayload() throws Exception {
    UUID eventId = UUID.randomUUID();
    var result = service.append(command(eventId, TENANT_ONE, Map.of("reasonCode", "created")));

    assertThat(result.duplicate()).isFalse();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_audit_records", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_outbox", Integer.class))
        .isEqualTo(1);
    String payload =
        jdbc.queryForObject("SELECT payload_json FROM governance_outbox", String.class);
    JsonNode event = new ObjectMapper().readTree(payload);
    assertThat(event.get("eventType").asText()).isEqualTo("governance.audit.appended");
    assertThat(event.at("/data/summary/reasonCode").asText()).isEqualTo("created");

    var claimed = adapter.claimPending(UUID.fromString(TENANT_ONE), 10);
    assertThat(claimed)
        .singleElement()
        .satisfies(entry -> assertThat(entry.attempts()).isEqualTo(1));
    assertThat(adapter.markDelivered(UUID.fromString(TENANT_ONE), eventId)).isTrue();
    assertThat(adapter.claimPending(UUID.fromString(TENANT_ONE), 10)).isEmpty();
  }

  @Test
  void duplicateEventIsIdempotentButDifferentFingerprintConflicts() {
    UUID eventId = UUID.randomUUID();
    var first = service.append(command(eventId, TENANT_ONE, Map.of("reasonCode", "created")));
    var duplicate = service.append(command(eventId, TENANT_ONE, Map.of("reasonCode", "created")));

    assertThat(duplicate.duplicate()).isTrue();
    assertThat(duplicate.record().id()).isEqualTo(first.record().id());
    assertThatThrownBy(
            () -> service.append(command(eventId, TENANT_ONE, Map.of("reasonCode", "deleted"))))
        .isInstanceOf(GovernanceAuditException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceAuditException) error).code())
                    .isEqualTo(GovernanceAuditException.IDEMPOTENCY_CONFLICT_CODE));
  }

  @Test
  void rejectsSensitiveOrUnknownSummaryWithoutPersistence() {
    assertThatThrownBy(
            () -> service.append(command(UUID.randomUUID(), TENANT_ONE, Map.of("secret", "value"))))
        .isInstanceOf(GovernanceAuditException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceAuditException) error).code())
                    .isEqualTo(GovernanceAuditException.VALIDATION_ERROR_CODE));
    assertThatThrownBy(
            () ->
                service.append(
                    command(
                        UUID.randomUUID(),
                        TENANT_ONE,
                        Map.of("reasonCode", "Authorization: Bearer token"))))
        .isInstanceOf(GovernanceAuditException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_audit_records", Integer.class))
        .isZero();
  }

  @Test
  void hashChainIsTenantScoped() {
    var first = service.append(command(UUID.randomUUID(), TENANT_ONE, Map.of()));
    var second = service.append(command(UUID.randomUUID(), TENANT_ONE, Map.of()));
    bind(TENANT_TWO);
    var otherTenant = service.append(command(UUID.randomUUID(), TENANT_TWO, Map.of()));

    assertThat(second.record().previousHash()).isEqualTo(first.record().recordHash());
    assertThat(otherTenant.record().previousHash()).isNull();
  }

  @Test
  void transactionRollsBackBothAuditAndOutbox() {
    var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      service.append(command(UUID.randomUUID(), TENANT_ONE, Map.of()));
                      throw new IllegalStateException("force rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_audit_records", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM governance_outbox", Integer.class))
        .isZero();
  }

  @Test
  void retriesAndQuarantinesPendingOutbox() {
    UUID eventId = UUID.randomUUID();
    service.append(command(eventId, TENANT_ONE, Map.of()));
    assertThat(adapter.markRetryable(UUID.fromString(TENANT_ONE), eventId)).isTrue();
    assertThat(adapter.claimPending(UUID.fromString(TENANT_ONE), 1)).hasSize(1);
    assertThat(adapter.quarantine(UUID.fromString(TENANT_ONE), eventId)).isTrue();
    assertThat(jdbc.queryForObject("SELECT status FROM governance_outbox", String.class))
        .isEqualTo("QUARANTINED");
  }

  private void bind(String tenantId) {
    TenantContextHolder.bind(
        new TenantContext(
            UUID.fromString(tenantId),
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            java.util.Set.of("ROLE_OPERATOR"),
            "policy-1",
            "request-1",
            "0123456789abcdef0123456789abcdef",
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private com.openeip.governance.domain.audit.AuditAppendCommand command(
      UUID eventId, String tenantId, Map<String, Object> summary) {
    return AuditService.command(
        eventId,
        UUID.fromString(tenantId),
        PRINCIPAL,
        "tenant.updated",
        "tenant",
        tenantId,
        AuditOutcome.SUCCESS,
        "request-1",
        "0123456789abcdef0123456789abcdef",
        "policy-1",
        occurredAt,
        summary);
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
