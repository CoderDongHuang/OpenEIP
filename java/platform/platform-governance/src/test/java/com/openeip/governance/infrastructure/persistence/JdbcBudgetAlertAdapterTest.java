package com.openeip.governance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openeip.governance.application.audit.AuditService;
import com.openeip.governance.application.budget.BudgetAlertService;
import com.openeip.governance.application.budget.BudgetService;
import com.openeip.governance.application.context.TenantContextHolder;
import com.openeip.governance.application.usage.UsageLedgerPort;
import com.openeip.governance.domain.budget.BudgetAlertRegistration;
import com.openeip.governance.domain.budget.BudgetAlertStatus;
import com.openeip.governance.domain.budget.BudgetDecisionRequest;
import com.openeip.governance.domain.budget.BudgetDecisionType;
import com.openeip.governance.domain.budget.BudgetRegistration;
import com.openeip.governance.domain.budget.BudgetWindowType;
import com.openeip.governance.domain.context.GovernanceScope;
import com.openeip.governance.domain.context.TenantContext;
import com.openeip.governance.shared.exception.GovernanceCatalogException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

class JdbcBudgetAlertAdapterTest {
  private static final String TENANT_ONE = "11111111-1111-4111-8111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-4222-8222-222222222222";
  private static final UUID PRINCIPAL = UUID.fromString("33333333-3333-4333-8333-333333333333");
  private static final String REQUEST_ID = "request-budget-1";
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
  private JdbcTemplate jdbc;
  private JdbcBudgetAlertAdapter adapter;
  private BudgetService budgetService;
  private BudgetAlertService alertService;
  private UUID budgetId;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL(
        "jdbc:h2:mem:governance-budget-"
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
    insertTenant(TENANT_ONE, "tenant-one");
    insertTenant(TENANT_TWO, "tenant-two");
    var audit = new AuditService(new JdbcAuditOutboxAdapter(jdbc, new ObjectMapper()));
    adapter = new JdbcBudgetAlertAdapter(jdbc);
    UsageLedgerPort usage = new JdbcPricingUsageAdapter(jdbc);
    budgetService = new BudgetService(adapter, usage, audit);
    alertService = new BudgetAlertService(adapter, adapter, audit);
    bind(TENANT_ONE);
    budgetId =
        budgetService
            .create(
                new BudgetRegistration(
                    UUID.fromString(TENANT_ONE),
                    "daily-budget",
                    "USD",
                    new BigDecimal("10.000000"),
                    BudgetWindowType.DAILY,
                    NOW))
            .id();
  }

  @AfterEach
  void tearDown() {
    TenantContextHolder.clear();
  }

  @Test
  void allowsFirstReservationAndDeniesCrossExecutionOversell() {
    var first = decision(execution(1), BudgetDecisionType.START, "6.000000");
    var second = decision(execution(2), BudgetDecisionType.START, "5.000000");

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isFalse();
    assertThat(second.errorCode()).isEqualTo("GOV-B-001");
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM governance_budget_decisions", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void checkpointCanOnlyReleaseReservationAndHistoryIsAppendOnly() {
    var execution = execution(3);
    assertThat(decision(execution, BudgetDecisionType.START, "8.000000", NOW).allowed()).isTrue();
    assertThat(
            decision(execution, BudgetDecisionType.CHECKPOINT, "4.000000", NOW.plusSeconds(1))
                .allowed())
        .isTrue();
    assertThatThrownBy(
            () ->
                decision(execution, BudgetDecisionType.CHECKPOINT, "5.000000", NOW.plusSeconds(2)))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));
    assertThat(
            adapter
                .latestDecision(UUID.fromString(TENANT_ONE), budgetId, execution)
                .orElseThrow()
                .reservedAmount())
        .isEqualByComparingTo("4.000000");
    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM governance_budget_decisions", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void budgetAndAlertsAreTenantScopedAndAlertTransitionsAreIdempotentAtTheStorageBoundary() {
    var registration =
        new BudgetAlertRegistration(
            UUID.fromString(TENANT_ONE), budgetId, NOW, new BigDecimal("0.80000"), 1, NOW);
    var first = alertService.create(registration);
    var retry = alertService.create(registration);

    assertThat(first.duplicate()).isFalse();
    assertThat(retry.duplicate()).isTrue();
    assertThat(retry.alert().id()).isEqualTo(first.alert().id());
    assertThat(alertService.markSent(first.alert().id()).status())
        .isEqualTo(BudgetAlertStatus.SENT);
    assertThat(alertService.acknowledge(first.alert().id()).status())
        .isEqualTo(BudgetAlertStatus.ACKNOWLEDGED);
    assertThatThrownBy(() -> alertService.markSent(first.alert().id()))
        .isInstanceOf(GovernanceCatalogException.class);

    bind(TENANT_TWO);
    assertThat(adapter.budget(UUID.fromString(TENANT_TWO), budgetId)).isEmpty();
    assertThatThrownBy(() -> alertService.get(first.alert().id()))
        .isInstanceOf(GovernanceCatalogException.class);
  }

  @Test
  void checkpointRequiresAnExistingAllowedStart() {
    assertThatThrownBy(() -> decision(execution(4), BudgetDecisionType.CHECKPOINT, "1.000000"))
        .isInstanceOf(GovernanceCatalogException.class)
        .satisfies(
            error ->
                assertThat(((GovernanceCatalogException) error).code()).isEqualTo("GOV-C-002"));
  }

  private com.openeip.governance.domain.budget.BudgetDecisionResult decision(
      UUID executionId, BudgetDecisionType type, String reservation) {
    return decision(executionId, type, reservation, NOW);
  }

  private com.openeip.governance.domain.budget.BudgetDecisionResult decision(
      UUID executionId, BudgetDecisionType type, String reservation, Instant now) {
    return budgetService.decide(
        new BudgetDecisionRequest(
            UUID.fromString(TENANT_ONE),
            budgetId,
            executionId,
            type,
            new BigDecimal(reservation),
            now));
  }

  private UUID execution(int value) {
    return UUID.fromString(String.format("44444444-4444-4444-8444-%012d", value));
  }

  private void bind(String tenantId) {
    TenantContextHolder.bind(
        new TenantContext(
            UUID.fromString(tenantId),
            null,
            PRINCIPAL,
            UUID.randomUUID(),
            Set.of("ROLE_OPERATOR"),
            "policy-1",
            REQUEST_ID,
            TRACE_ID,
            GovernanceScope.TENANT,
            Instant.parse("2099-01-01T00:00:00Z")));
  }

  private void insertTenant(String id, String slug) {
    jdbc.update(
        """
        INSERT INTO governance_tenants
          (id, tenant_id, display_name, slug, state, policy_version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'policy-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        id,
        id,
        slug,
        slug);
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
